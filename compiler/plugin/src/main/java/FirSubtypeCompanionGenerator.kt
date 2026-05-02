/*
 * Copyright (C) 2026 Jason Monk <monkopedia@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.monkopedia.ksrpc.plugin

import com.monkopedia.ksrpc.plugin.FqConstants.CREATE
import com.monkopedia.ksrpc.plugin.FqConstants.KS_METHOD
import com.monkopedia.ksrpc.plugin.FqConstants.KS_SERVICE
import com.monkopedia.ksrpc.plugin.FqConstants.KTYPE
import com.monkopedia.ksrpc.plugin.FqConstants.RPC_OBJECT
import com.monkopedia.ksrpc.plugin.FqConstants.RPC_OBJECT_FACTORY
import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.extensions.NestedClassGenerationContext
import org.jetbrains.kotlin.fir.extensions.predicate.LookupPredicate
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.plugin.createCompanionObject
import org.jetbrains.kotlin.fir.plugin.createConeType
import org.jetbrains.kotlin.fir.plugin.createConstructor
import org.jetbrains.kotlin.fir.plugin.createDefaultPrivateConstructor
import org.jetbrains.kotlin.fir.plugin.createMemberFunction
import org.jetbrains.kotlin.fir.plugin.createMemberProperty
import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.scopes.impl.toConeType
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeStarProjection
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames

/**
 * FIR declaration generator for plain-Kotlin subtypes of `@KsService` interfaces.
 *
 * When a non-`@KsService` interface transitively extends a `@KsService` interface,
 * this generator synthesizes a companion object on the subtype so that
 * `rpcObject<Subtype>()` can resolve on all platforms (including Native/JS/WASM
 * where `findAssociatedObject` is the only dispatch mechanism).
 *
 * Two scenarios:
 * - **Scenario 1 (non-generic subtype)**: `interface TypedFoo : GenericService<String>`
 *   Companion implements `RpcObject<TypedFoo>` and delegates to the parent's factory.
 * - **Scenario 2 (generic subtype)**: `interface TypedFooT<T> : GenericService<T>`
 *   Companion implements `RpcObjectFactory<TypedFooT<*>>` and delegates `create(typeArgs)`
 *   through the subtype-to-parent type-arg substitution.
 *
 * See issue #95.
 */
class FirSubtypeCompanionGenerator(session: FirSession) :
    FirDeclarationGenerationExtension(session) {

    // Predicates for identifying @KsService-annotated classes (same as
    // FirCompanionDeclarationGenerator).
    private val ksServicePredicates =
        listOf(KS_METHOD, KS_SERVICE).map { LookupPredicate.create { annotated(it) } }

    // Predicate that matches classes annotated with @KsService OR whose supertypes are.
    // `annotatedOrUnder` checks annotations transitively through the supertype chain.
    private val annotatedOrUnderPredicates =
        listOf(KS_SERVICE).map { LookupPredicate.create { annotatedOrUnder(it) } }

    // Older versions of ksrpc-core (pre-#41) don't ship RpcObjectFactory. When that class
    // isn't on the classpath we skip the factory supertype and the arity/create callables.
    private val factorySupported: Boolean by lazy {
        session.symbolProvider.getClassLikeSymbolByClassId(RPC_OBJECT_FACTORY) != null
    }

    // Cache to avoid repeated supertype walks. Maps ClassId -> nearest @KsService ancestor
    // ClassId, or null if none found.
    private val ksServiceAncestorCache = mutableMapOf<ClassId, ClassId?>()

    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        register(annotatedOrUnderPredicates)
    }

    /**
     * Check if [symbol] is annotated with @KsService or @KsMethod using the predicate-based
     * provider, which is safe to call during the generation phase. The predicates are
     * registered by [FirCompanionDeclarationGenerator], not by this generator.
     */
    private fun isKsService(symbol: FirClassSymbol<*>): Boolean =
        session.predicateBasedProvider.matches(ksServicePredicates, symbol)

    /**
     * Walk supertypes of [classId] to find the nearest @KsService-annotated ancestor.
     * Returns null if no @KsService ancestor exists.
     *
     * Uses `resolvedSuperTypeRefs` to safely walk the supertype chain. When supertypes
     * haven't been resolved yet (FirUserTypeRef), we attempt to resolve the ClassId from
     * the type ref's text and look it up via the symbol provider. This handles the common
     * case where the @KsService parent is in a different file/module and has already been
     * resolved, but the subtype's FIR tree still has unresolved type refs.
     */
    @OptIn(SymbolInternals::class)
    private fun findKsServiceAncestor(classId: ClassId, ownerPackage: FqName): ClassId? {
        ksServiceAncestorCache[classId]?.let { return it }
        val visited = mutableSetOf<ClassId>()
        val queue = ArrayDeque<ClassId>()
        queue.add(classId)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (!visited.add(current)) continue
            val symbol = session.symbolProvider.getClassLikeSymbolByClassId(current)
                as? FirRegularClassSymbol ?: continue
            if (current != classId && isKsService(symbol)) {
                ksServiceAncestorCache[classId] = current
                return current
            }
            // Walk supertype refs, handling both resolved and unresolved cases.
            for (superRef in symbol.fir.superTypeRefs) {
                val superClassId = extractClassId(superRef, ownerPackage)
                if (superClassId != null) {
                    queue.add(superClassId)
                }
            }
        }
        ksServiceAncestorCache[classId] = null
        return null
    }

    /**
     * Extract a ClassId from a FirTypeRef, handling both resolved and unresolved cases.
     */
    private fun extractClassId(
        typeRef: org.jetbrains.kotlin.fir.types.FirTypeRef,
        ownerPackage: FqName
    ): ClassId? {
        // Resolved type ref — extract directly from the cone type.
        if (typeRef is FirResolvedTypeRef) {
            val coneType = typeRef.coneType as? ConeClassLikeType ?: return null
            return coneType.lookupTag.classId
        }
        // Unresolved user type ref — try to reconstruct the ClassId from qualifier segments.
        if (typeRef is org.jetbrains.kotlin.fir.types.FirUserTypeRef) {
            return resolveUserTypeRef(typeRef, ownerPackage)
        }
        return null
    }

    /**
     * Attempt to resolve a FirUserTypeRef to a ClassId by building candidate FQNs from
     * the qualifier segments and checking the symbol provider.
     */
    private fun resolveUserTypeRef(
        userTypeRef: org.jetbrains.kotlin.fir.types.FirUserTypeRef,
        ownerPackage: FqName
    ): ClassId? {
        val qualifiers = userTypeRef.qualifier
        if (qualifiers.isEmpty()) return null
        val segments = qualifiers.map { it.name }
        // Try single-segment name (common case: imported or same-package type)
        if (segments.size == 1) {
            val simpleName = segments[0]
            // Try the owner's package first (most common case)
            val samePackageCandidate = ClassId(ownerPackage, simpleName)
            if (session.symbolProvider.getClassLikeSymbolByClassId(samePackageCandidate) != null) {
                return samePackageCandidate
            }
            // Look up in common ksrpc packages
            for (pkg in COMMON_PACKAGES) {
                if (pkg == ownerPackage) continue // already tried
                val candidate = ClassId(pkg, simpleName)
                if (session.symbolProvider.getClassLikeSymbolByClassId(candidate) != null) {
                    return candidate
                }
            }
            // Try root package
            val rootCandidate = ClassId(FqName.ROOT, simpleName)
            if (session.symbolProvider.getClassLikeSymbolByClassId(rootCandidate) != null) {
                return rootCandidate
            }
        }
        // Try multi-segment qualified name
        for (splitAt in 1..segments.size) {
            val packageParts = segments.subList(0, splitAt).map { it.asString() }
            val classNameParts = segments.subList(splitAt, segments.size)
            if (classNameParts.isEmpty()) continue
            val packageFqName = FqName(packageParts.joinToString("."))
            val className = classNameParts[0]
            val candidate = ClassId(packageFqName, className)
            if (session.symbolProvider.getClassLikeSymbolByClassId(candidate) != null) {
                return candidate
            }
        }
        return null
    }

    companion object {
        // Common packages where @KsService interfaces are likely declared.
        private val COMMON_PACKAGES = listOf(
            FqName("com.monkopedia.ksrpc"),
            FqName("com.monkopedia.ksrpc.flow")
        )
    }

    override fun getNestedClassifiersNames(
        classSymbol: FirClassSymbol<*>,
        context: NestedClassGenerationContext
    ): Set<Name> {
        val regularSymbol = classSymbol as? FirRegularClassSymbol ?: return emptySet()
        // Skip classes that are @KsService themselves — those are handled by
        // FirCompanionDeclarationGenerator.
        if (isKsService(regularSymbol)) return emptySet()
        // Only process interfaces (plain Kotlin subtypes).
        if (regularSymbol.classKind != org.jetbrains.kotlin.descriptors.ClassKind.INTERFACE) {
            return emptySet()
        }
        // Find the specific @KsService ancestor. Use the class's own package as a hint
        // for resolving unqualified user type refs.
        val ownerPackage = regularSymbol.classId.packageFqName
        val ksServiceAncestor = findKsServiceAncestor(regularSymbol.classId, ownerPackage)
            ?: return emptySet()
        // Found a @KsService ancestor — advertise the companion.
        return setOf(SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT)
    }

    override fun generateNestedClassLikeDeclaration(
        owner: FirClassSymbol<*>,
        name: Name,
        context: NestedClassGenerationContext
    ): FirClassLikeSymbol<*>? {
        if (name != SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT) return null
        val regularOwner = owner as? FirRegularClassSymbol ?: return null
        val ownerPackage = regularOwner.classId.packageFqName
        val parentServiceId = findKsServiceAncestor(regularOwner.classId, ownerPackage)
            ?: return null

        val isGeneric = regularOwner.typeParameterSymbols.isNotEmpty()

        return if (!isGeneric) {
            // Scenario 1: non-generic subtype — companion implements RpcObject<Subtype>
            createCompanionObject(
                owner,
                Key(
                    subtypeClassId = owner.classId,
                    parentServiceClassId = parentServiceId,
                    isGeneric = false
                )
            ) {
                superType(RPC_OBJECT.createConeType(session, arrayOf(owner.defaultType())))
            }.symbol
        } else {
            // Scenario 2: generic subtype — companion implements RpcObjectFactory<Subtype<*,...>>
            val starProjections =
                Array<org.jetbrains.kotlin.fir.types.ConeTypeProjection>(
                    owner.typeParameterSymbols.size
                ) { ConeStarProjection }
            val subtypeStarType = owner.classId.createConeType(session, starProjections)
            createCompanionObject(
                owner,
                Key(
                    subtypeClassId = owner.classId,
                    parentServiceClassId = parentServiceId,
                    isGeneric = true
                )
            ) {
                if (factorySupported) {
                    superType(
                        RPC_OBJECT_FACTORY.createConeType(session, arrayOf(subtypeStarType))
                    )
                }
            }.symbol
        }
    }

    private fun checkOwnerKey(context: MemberGenerationContext): Key? =
        (context.owner.origin as? FirDeclarationOrigin.Plugin)?.key as? Key

    override fun generateConstructors(
        context: MemberGenerationContext
    ): List<FirConstructorSymbol> {
        val ownerKey = checkOwnerKey(context) ?: return emptyList()
        // Use createConstructor instead of createDefaultPrivateConstructor to avoid
        // "Required value was null" errors on Native when the companion's supertypes
        // (RpcObject/RpcObjectFactory) are interfaces without a no-arg constructor.
        val constructor = createConstructor(
            context.owner,
            ownerKey,
            isPrimary = true,
            generateDelegatedNoArgConstructorCall = false
        )
        return listOf(constructor.symbol)
    }

    override fun generateFunctions(
        callableId: CallableId,
        context: MemberGenerationContext?
    ): List<FirNamedFunctionSymbol> {
        val ownerKey = context?.let(::checkOwnerKey) ?: return emptyList()
        return when (callableId.callableName) {
            FqConstants.FIND_ENDPOINT -> if (!ownerKey.isGeneric) {
                createFindEndpoint(callableId, context.owner, ownerKey)
            } else {
                emptyList()
            }

            FqConstants.CREATE_STUB -> if (!ownerKey.isGeneric) {
                createCreateStub(callableId, context.owner, ownerKey)
            } else {
                emptyList()
            }

            CREATE -> if (ownerKey.isGeneric && factorySupported) {
                createCreateFactory(callableId, context.owner, ownerKey)
            } else {
                emptyList()
            }

            else -> emptyList()
        }
    }

    override fun generateProperties(
        callableId: CallableId,
        context: MemberGenerationContext?
    ): List<FirPropertySymbol> {
        val ownerKey = context?.let(::checkOwnerKey) ?: return emptyList()
        if (ownerKey.isGeneric) {
            return when (callableId.callableName) {
                FqConstants.ARITY -> if (factorySupported) {
                    createArity(callableId, context.owner, ownerKey)
                } else {
                    emptyList()
                }

                else -> emptyList()
            }
        }
        return when (callableId.callableName) {
            FqConstants.SERVICE_NAME -> createServiceName(callableId, context.owner, ownerKey)
            FqConstants.ENDPOINTS -> createEndpoints(callableId, context.owner, ownerKey)
            else -> emptyList()
        }
    }

    private fun createCreateStub(
        callableId: CallableId,
        owner: FirClassSymbol<*>,
        ownerKey: Key
    ): List<FirNamedFunctionSymbol> {
        val retType = ownerKey.subtypeClassId.createConeType(session)
        val function =
            createMemberFunction(owner, ownerKey, callableId.callableName, retType) {
                modality = Modality.FINAL
                typeParameter(Name.identifier("S"))
                valueParameter(
                    Name.identifier("channel"),
                    { types ->
                        FqConstants.SERIALIZED_SERVICE.createConeType(
                            session,
                            arrayOf(types[0].toConeType())
                        )
                    }
                )
            }
        return listOf(function.symbol)
    }

    private fun createFindEndpoint(
        callableId: CallableId,
        owner: FirClassSymbol<*>,
        ownerKey: Key
    ): List<FirNamedFunctionSymbol> {
        val retType =
            FqConstants.RPC_METHOD.createConeType(session, Array(3) { ConeStarProjection })
        val function = createMemberFunction(owner, ownerKey, callableId.callableName, retType) {
            modality = Modality.FINAL
            valueParameter(
                Name.identifier("endpoint"),
                session.builtinTypes.stringType.coneType
            )
        }
        return listOf(function.symbol)
    }

    private fun createServiceName(
        callableId: CallableId,
        owner: FirClassSymbol<*>,
        ownerKey: Key
    ): List<FirPropertySymbol> {
        val property = createMemberProperty(
            owner,
            ownerKey,
            callableId.callableName,
            session.builtinTypes.stringType.coneType,
            isVal = true,
            hasBackingField = false
        ) {
            modality = Modality.FINAL
        }
        return listOf(property.symbol)
    }

    private fun createEndpoints(
        callableId: CallableId,
        owner: FirClassSymbol<*>,
        ownerKey: Key
    ): List<FirPropertySymbol> {
        val listType = LIST_CLASS_ID
            .createConeType(session, arrayOf(session.builtinTypes.stringType.coneType))
        val property = createMemberProperty(
            owner,
            ownerKey,
            callableId.callableName,
            listType,
            isVal = true,
            hasBackingField = false
        ) {
            modality = Modality.FINAL
        }
        return listOf(property.symbol)
    }

    private fun createArity(
        callableId: CallableId,
        owner: FirClassSymbol<*>,
        ownerKey: Key
    ): List<FirPropertySymbol> {
        val property = createMemberProperty(
            owner,
            ownerKey,
            callableId.callableName,
            session.builtinTypes.intType.coneType,
            isVal = true,
            hasBackingField = false
        ) {
            modality = Modality.FINAL
            status {
                isOverride = true
            }
        }
        return listOf(property.symbol)
    }

    private fun createCreateFactory(
        callableId: CallableId,
        owner: FirClassSymbol<*>,
        ownerKey: Key
    ): List<FirNamedFunctionSymbol> {
        val subtypeSymbol =
            session.symbolProvider.getClassLikeSymbolByClassId(ownerKey.subtypeClassId)
                as? FirRegularClassSymbol
                ?: return emptyList()
        val subtypeTypeParams = subtypeSymbol.typeParameterSymbols
        if (subtypeTypeParams.isEmpty()) return emptyList()
        val starProjections = Array<org.jetbrains.kotlin.fir.types.ConeTypeProjection>(
            subtypeTypeParams.size
        ) { ConeStarProjection }
        val subtypeStarType = ownerKey.subtypeClassId.createConeType(session, starProjections)
        val retType = RPC_OBJECT.createConeType(session, arrayOf(subtypeStarType))
        val listOfKType =
            LIST_CLASS_ID.createConeType(session, arrayOf(KTYPE.createConeType(session)))
        val function = createMemberFunction(owner, ownerKey, callableId.callableName, retType) {
            modality = Modality.FINAL
            status {
                isOverride = true
            }
            valueParameter(Name.identifier("typeArgs"), listOfKType)
        }
        return listOf(function.symbol)
    }

    override fun getCallableNamesForClass(
        classSymbol: FirClassSymbol<*>,
        context: MemberGenerationContext
    ): Set<Name> {
        if (classSymbol.classKind != org.jetbrains.kotlin.descriptors.ClassKind.OBJECT) {
            return emptySet()
        }
        (classSymbol as? FirRegularClassSymbol)?.classId
            ?.takeIf { it.isNestedClass }
            ?.takeIf { it.shortClassName == SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT }
            ?: return emptySet()

        val origin = classSymbol.origin as? FirDeclarationOrigin.Plugin
        val key = origin?.key as? Key ?: return emptySet()
        return if (key.isGeneric) {
            if (factorySupported) {
                setOf(CREATE, FqConstants.ARITY, SpecialNames.INIT)
            } else {
                setOf(SpecialNames.INIT)
            }
        } else {
            setOf(
                FqConstants.FIND_ENDPOINT,
                FqConstants.CREATE_STUB,
                FqConstants.SERVICE_NAME,
                FqConstants.ENDPOINTS,
                SpecialNames.INIT
            )
        }
    }

    /**
     * Key for generated subtype companion declarations. Carries the subtype and parent
     * service ClassIds so the IR transformer can locate the parent's companion.
     */
    data class Key(
        val subtypeClassId: ClassId,
        val parentServiceClassId: ClassId,
        val isGeneric: Boolean,
        val type: String = subtypeClassId.asFqNameString()
    ) : GeneratedDeclarationKey() {
        override fun toString(): String =
            if (isGeneric) {
                "KsrpcSubtypeGeneric($type->$parentServiceClassId)"
            } else {
                "KsrpcSubtype($type->$parentServiceClassId)"
            }
    }
}
