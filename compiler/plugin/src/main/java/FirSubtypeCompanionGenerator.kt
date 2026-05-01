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
import org.jetbrains.kotlin.fir.plugin.createDefaultPrivateConstructor
import org.jetbrains.kotlin.fir.plugin.createMemberFunction
import org.jetbrains.kotlin.fir.plugin.createMemberProperty
import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.scopes.impl.toConeType
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeStarProjection
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
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

    // Use the same predicates as FirCompanionDeclarationGenerator to identify @KsService
    // classes. The predicateBasedProvider is safe to query during the generation phase
    // because it's built from annotation indices, not resolved type refs.
    private val ksServicePredicates =
        listOf(KS_METHOD, KS_SERVICE).map { LookupPredicate.create { annotated(it) } }

    // Older versions of ksrpc-core (pre-#41) don't ship RpcObjectFactory. When that class
    // isn't on the classpath we skip the factory supertype and the arity/create callables.
    private val factorySupported: Boolean by lazy {
        session.symbolProvider.getClassLikeSymbolByClassId(RPC_OBJECT_FACTORY) != null
    }

    // Cache to avoid repeated supertype walks. Maps ClassId -> nearest @KsService ancestor
    // ClassId, or null if none found.
    private val ksServiceAncestorCache = mutableMapOf<ClassId, ClassId?>()

    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        // Register the same predicates so the predicateBasedProvider knows about @KsService
        // classes. This is required for `predicateBasedProvider.matches()` to work.
        register(ksServicePredicates)
    }

    /**
     * Check if [symbol] is annotated with @KsService or @KsMethod using the predicate-based
     * provider, which is safe to call during the generation phase.
     */
    private fun isKsService(symbol: FirClassSymbol<*>): Boolean =
        session.predicateBasedProvider.matches(ksServicePredicates, symbol)

    /**
     * Walk supertypes of [classId] to find the nearest @KsService-annotated ancestor.
     * Returns null if no @KsService ancestor exists.
     *
     * Only inspects resolved supertype refs to avoid triggering resolution of unresolved
     * types during the companion generation phase.
     */
    @OptIn(SymbolInternals::class)
    private fun findKsServiceAncestor(classId: ClassId): ClassId? {
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
            // Only walk resolved supertype refs to avoid triggering resolution.
            for (superRef in symbol.fir.superTypeRefs) {
                val resolvedRef = superRef as? FirResolvedTypeRef ?: continue
                val coneType = resolvedRef.coneType as? ConeClassLikeType ?: continue
                queue.add(coneType.lookupTag.classId)
            }
        }
        ksServiceAncestorCache[classId] = null
        return null
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
        val ksServiceAncestor = findKsServiceAncestor(regularSymbol.classId) ?: return emptySet()
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
        val parentServiceId = findKsServiceAncestor(regularOwner.classId) ?: return null

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
        val constructor = createDefaultPrivateConstructor(context.owner, ownerKey)
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
