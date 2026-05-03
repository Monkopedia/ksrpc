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

import com.monkopedia.ksrpc.plugin.FqConstants.ARITY
import com.monkopedia.ksrpc.plugin.FqConstants.CREATE
import com.monkopedia.ksrpc.plugin.FqConstants.CREATE_STUB
import com.monkopedia.ksrpc.plugin.FqConstants.ENDPOINTS
import com.monkopedia.ksrpc.plugin.FqConstants.FIND_ENDPOINT
import com.monkopedia.ksrpc.plugin.FqConstants.INVOKE
import com.monkopedia.ksrpc.plugin.FqConstants.KSERIALIZER
import com.monkopedia.ksrpc.plugin.FqConstants.KS_METHOD
import com.monkopedia.ksrpc.plugin.FqConstants.KS_SERVICE
import com.monkopedia.ksrpc.plugin.FqConstants.KTYPE
import com.monkopedia.ksrpc.plugin.FqConstants.RPC_METHOD
import com.monkopedia.ksrpc.plugin.FqConstants.RPC_OBJECT
import com.monkopedia.ksrpc.plugin.FqConstants.RPC_OBJECT_FACTORY
import com.monkopedia.ksrpc.plugin.FqConstants.SERIALIZED_SERVICE
import com.monkopedia.ksrpc.plugin.FqConstants.SERVICE_NAME
import com.monkopedia.ksrpc.plugin.FqConstants.SERVICE_TIER
import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality.FINAL
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
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.ConeStarProjection
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.types.Variance

class FirCompanionDeclarationGenerator(session: FirSession) :
    FirDeclarationGenerationExtension(session) {

    private val predicates =
        listOf(KS_METHOD, KS_SERVICE).map { LookupPredicate.create { annotated(it) } }

    // Older versions of ksrpc-core (pre-#41) don't ship RpcObjectFactory. When that class
    // isn't on the classpath we skip the factory supertype and the arity/create callables so
    // compilation against older cores still succeeds.
    private val factorySupported: Boolean by lazy {
        session.symbolProvider.getClassLikeSymbolByClassId(RPC_OBJECT_FACTORY) != null
    }

    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        register(predicates)
    }

    override fun generateNestedClassLikeDeclaration(
        owner: FirClassSymbol<*>,
        name: Name,
        context: NestedClassGenerationContext
    ): FirClassLikeSymbol<*>? {
        if (name != SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT) return null
        // Always generate the companion. For non-generic services the companion directly
        // implements RpcObject<Service>. For generic services the companion is a plain
        // object exposing `operator fun <T, ...> invoke(serializers): RpcObject<Service<T, ...>>`,
        // so users always reach an `RpcObject<Service<...>>` from the service symbol.
        return if (owner.typeParameterSymbols.isEmpty()) {
            createCompanionObject(owner, Key(owner.classId, isGeneric = false)) {
                superType(RPC_OBJECT.createConeType(session, arrayOf(owner.defaultType())))
            }.symbol
        } else {
            // Companion for a generic service implements
            // RpcObjectFactory<Service<*, *, ...>>, so callers with only KTypes (e.g.
            // sub-service transformers, introspection) can reach an RpcObject through the
            // factory. The typed `operator fun invoke(ser, ...)` remains the preferred API.
            val starProjections = Array<org.jetbrains.kotlin.fir.types.ConeTypeProjection>(
                owner.typeParameterSymbols.size
            ) { ConeStarProjection }
            val serviceStarType = owner.classId.createConeType(session, starProjections)
            createCompanionObject(owner, Key(owner.classId, isGeneric = true)) {
                if (factorySupported) {
                    superType(
                        RPC_OBJECT_FACTORY.createConeType(session, arrayOf(serviceStarType))
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
            FIND_ENDPOINT -> if (ownerKey.isGeneric) {
                emptyList()
            } else {
                createFindEndpoint(callableId, context.owner, ownerKey)
            }

            CREATE_STUB -> if (ownerKey.isGeneric) {
                emptyList()
            } else {
                createCreateStub(callableId, context.owner, ownerKey)
            }

            INVOKE -> if (ownerKey.isGeneric) {
                createInvokeFactory(callableId, context.owner, ownerKey)
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
                ARITY -> if (factorySupported) {
                    createArity(callableId, context.owner, ownerKey)
                } else {
                    emptyList()
                }

                else -> emptyList()
            }
        }
        return when (callableId.callableName) {
            SERVICE_NAME -> createServiceName(callableId, context.owner, ownerKey)
            ENDPOINTS -> createEndpoints(callableId, context.owner, ownerKey)
            SERVICE_TIER -> createServiceTier(callableId, context.owner, ownerKey)
            else -> emptyList()
        }
    }

    private fun createCreateStub(
        callableId: CallableId,
        owner: FirClassSymbol<*>,
        ownerKey: Key
    ): List<FirNamedFunctionSymbol> {
        val retType = ownerKey.classId.createConeType(session)
        val function =
            createMemberFunction(owner, ownerKey, callableId.callableName, retType) {
                modality = FINAL
                typeParameter(Name.identifier("S"))
                valueParameter(Name.identifier("channel"), { types ->
                    SERIALIZED_SERVICE.createConeType(session, arrayOf(types[0].toConeType()))
                })
            }
        return listOf(function.symbol)
    }

    private fun createInvokeFactory(
        callableId: CallableId,
        owner: FirClassSymbol<*>,
        ownerKey: Key
    ): List<FirNamedFunctionSymbol> {
        // Figure out how many type params the enclosing service carries.
        val serviceClassId = ownerKey.classId
        val serviceSymbol =
            session.symbolProvider.getClassLikeSymbolByClassId(serviceClassId)
                as? FirRegularClassSymbol
                ?: return emptyList()
        val serviceTypeParams = serviceSymbol.typeParameterSymbols
        if (serviceTypeParams.isEmpty()) return emptyList()
        val retTypeProvider: (List<org.jetbrains.kotlin.fir.declarations.FirTypeParameter>) ->
        ConeKotlinType = { tps ->
            // RpcObject<Service<T1, T2, ...>>
            val serviceType = ownerKey.classId.createConeTypeForRefs(session, tps)
            RPC_OBJECT.createConeType(session, arrayOf(serviceType))
        }
        val function = createMemberFunction(
            owner,
            ownerKey,
            callableId.callableName,
            retTypeProvider
        ) {
            modality = FINAL
            status {
                isOperator = true
            }
            for (tp in serviceTypeParams) {
                typeParameter(tp.name, Variance.INVARIANT, isReified = false)
            }
            addSerializerValueParameters(session, serviceTypeParams)
        }
        return listOf(function.symbol)
    }

    private fun createCreateFactory(
        callableId: CallableId,
        owner: FirClassSymbol<*>,
        ownerKey: Key
    ): List<FirNamedFunctionSymbol> {
        // override fun create(typeArgs: List<KType>): RpcObject<Service<*, *, ...>>
        val serviceClassId = ownerKey.classId
        val serviceSymbol =
            session.symbolProvider.getClassLikeSymbolByClassId(serviceClassId)
                as? FirRegularClassSymbol
                ?: return emptyList()
        val serviceTypeParams = serviceSymbol.typeParameterSymbols
        if (serviceTypeParams.isEmpty()) return emptyList()
        val starProjections = Array<org.jetbrains.kotlin.fir.types.ConeTypeProjection>(
            serviceTypeParams.size
        ) { ConeStarProjection }
        val serviceStarType = serviceClassId.createConeType(session, starProjections)
        val retType = RPC_OBJECT.createConeType(session, arrayOf(serviceStarType))
        val listOfKType =
            LIST_CLASS_ID.createConeType(session, arrayOf(KTYPE.createConeType(session)))
        val function = createMemberFunction(owner, ownerKey, callableId.callableName, retType) {
            modality = FINAL
            status {
                isOverride = true
            }
            valueParameter(Name.identifier("typeArgs"), listOfKType)
        }
        return listOf(function.symbol)
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
            modality = FINAL
            status {
                isOverride = true
            }
        }
        return listOf(property.symbol)
    }

    private fun createFindEndpoint(
        callableId: CallableId,
        owner: FirClassSymbol<*>,
        ownerKey: Key
    ): List<FirNamedFunctionSymbol> {
        val retType = RPC_METHOD.createConeType(session, Array(3) { ConeStarProjection })
        val function = createMemberFunction(owner, ownerKey, callableId.callableName, retType) {
            modality = FINAL
            valueParameter(Name.identifier("endpoint"), session.builtinTypes.stringType.coneType)
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
            modality = FINAL
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
            modality = FINAL
        }
        return listOf(property.symbol)
    }

    private fun createServiceTier(
        callableId: CallableId,
        owner: FirClassSymbol<*>,
        ownerKey: Key
    ): List<FirPropertySymbol> {
        val tierType = FqConstants.SERVICE_TIER_CLASS.createConeType(session)
        val property = createMemberProperty(
            owner,
            ownerKey,
            callableId.callableName,
            tierType,
            isVal = true,
            hasBackingField = false
        ) {
            modality = FINAL
            status {
                isOverride = true
            }
        }
        return listOf(property.symbol)
    }

    override fun getCallableNamesForClass(
        classSymbol: FirClassSymbol<*>,
        context: MemberGenerationContext
    ): Set<Name> {
        if (classSymbol.classKind != ClassKind.OBJECT) {
            return emptySet()
        }
        (classSymbol as? FirRegularClassSymbol)?.classId
            ?.takeIf { it.isNestedClass }
            ?.takeIf { it.shortClassName == SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT }
            ?: return emptySet()

        val origin = classSymbol.origin as? FirDeclarationOrigin.Plugin
        val key = origin?.key as? Key
        return when {
            key == null -> setOf(FIND_ENDPOINT, CREATE_STUB, SERVICE_NAME, ENDPOINTS, SERVICE_TIER)

            key.isGeneric -> if (factorySupported) {
                setOf(INVOKE, CREATE, ARITY, SpecialNames.INIT)
            } else {
                setOf(INVOKE, SpecialNames.INIT)
            }

            else -> setOf(
                FIND_ENDPOINT, CREATE_STUB, SERVICE_NAME, ENDPOINTS, SERVICE_TIER,
                SpecialNames.INIT
            )
        }
    }

    override fun getNestedClassifiersNames(
        classSymbol: FirClassSymbol<*>,
        context: NestedClassGenerationContext
    ): Set<Name> {
        if (!session.predicateBasedProvider.matches(predicates, classSymbol)) return emptySet()
        // Always advertise the companion. For generic services the companion exposes
        // `operator fun invoke(...)` to materialize an RpcObject. For non-generic services
        // the companion directly extends RpcObject.
        return setOf(SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT)
    }

    data class Key(
        val classId: ClassId,
        val isGeneric: Boolean = false,
        val type: String = classId.asFqNameString()
    ) : GeneratedDeclarationKey() {
        override fun toString(): String =
            if (isGeneric) "KsrpcServiceGeneric($type)" else "KsrpcService($type)"
    }
}
