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

import com.monkopedia.ksrpc.plugin.FqConstants.CREATE_STUB
import com.monkopedia.ksrpc.plugin.FqConstants.ENDPOINTS
import com.monkopedia.ksrpc.plugin.FqConstants.FIND_ENDPOINT
import com.monkopedia.ksrpc.plugin.FqConstants.KSERIALIZER
import com.monkopedia.ksrpc.plugin.FqConstants.KS_METHOD
import com.monkopedia.ksrpc.plugin.FqConstants.KS_SERVICE
import com.monkopedia.ksrpc.plugin.FqConstants.OBJ
import com.monkopedia.ksrpc.plugin.FqConstants.RPC_METHOD
import com.monkopedia.ksrpc.plugin.FqConstants.RPC_OBJECT
import com.monkopedia.ksrpc.plugin.FqConstants.SERIALIZED_SERVICE
import com.monkopedia.ksrpc.plugin.FqConstants.SERVICE_NAME
import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.extensions.NestedClassGenerationContext
import org.jetbrains.kotlin.fir.extensions.predicate.LookupPredicate
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.plugin.ClassBuildingContext
import org.jetbrains.kotlin.fir.plugin.createConeType
import org.jetbrains.kotlin.fir.plugin.createConstructor
import org.jetbrains.kotlin.fir.plugin.createMemberFunction
import org.jetbrains.kotlin.fir.plugin.createMemberProperty
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
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames

/**
 * For generic `@KsService` interfaces, generates a nested `Obj<T, ...>` class that
 * implements `RpcObject<Service<T, ...>>`. The companion-object `invoke<T, ...>(ser)`
 * returns an instance of this class. Users reach the `RpcObject` via `Service(ser)`,
 * not by naming `Obj` directly.
 *
 * For non-generic services there is no `Obj` — the companion-object itself directly
 * implements `RpcObject<Service>` (handled by [FirCompanionDeclarationGenerator]).
 */
class FirKsrpcObjGenerator(session: FirSession) : FirDeclarationGenerationExtension(session) {

    private val predicates =
        listOf(KS_METHOD, KS_SERVICE).map { LookupPredicate.create { annotated(it) } }

    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        register(predicates)
    }

    override fun generateNestedClassLikeDeclaration(
        owner: FirClassSymbol<*>,
        name: Name,
        context: NestedClassGenerationContext
    ): FirClassLikeSymbol<*>? {
        if (name != OBJ) return null
        val serviceTypeParams = owner.typeParameterSymbols
        if (serviceTypeParams.isEmpty()) return null
        val classId = owner.classId.createNestedClassId(OBJ)
        return ClassBuildingContext(
            session,
            Key(owner.classId.asFqNameString()),
            owner,
            classId,
            ClassKind.CLASS
        ).apply {
            modality = Modality.FINAL
            for (tp in serviceTypeParams) {
                typeParameter(tp.name)
            }
            superType { refs ->
                val serviceType = owner.classId.createConeType(
                    session,
                    refs.map { it.symbol.toConeType() }.toTypedArray()
                )
                RPC_OBJECT.createConeType(session, arrayOf(serviceType))
            }
        }.build().symbol
    }

    private fun checkOwnerKey(context: MemberGenerationContext): Key? =
        (context.owner.origin as? FirDeclarationOrigin.Plugin)?.key as? Key

    override fun generateConstructors(
        context: MemberGenerationContext
    ): List<FirConstructorSymbol> {
        val ownerKey = checkOwnerKey(context) ?: return emptyList()
        val owner = context.owner as FirRegularClassSymbol
        val objTypeParams = owner.typeParameterSymbols
        val constructor = createConstructor(owner, ownerKey, isPrimary = true) {
            for ((idx, tp) in objTypeParams.withIndex()) {
                valueParameter(
                    Name.identifier(tp.name.asString() + "Serializer"),
                    typeProvider = { refs ->
                        KSERIALIZER.createConeType(
                            session,
                            arrayOf(refs[idx].symbol.toConeType())
                        )
                    }
                )
            }
        }
        return listOf(constructor.symbol)
    }

    override fun generateFunctions(
        callableId: CallableId,
        context: MemberGenerationContext?
    ): List<FirNamedFunctionSymbol> {
        val ownerKey = context?.let(::checkOwnerKey) ?: return emptyList()
        val owner = context.owner as? FirRegularClassSymbol ?: return emptyList()
        return when (callableId.callableName) {
            CREATE_STUB -> createCreateStub(callableId, owner, ownerKey)
            FIND_ENDPOINT -> createFindEndpoint(callableId, owner, ownerKey)
            else -> emptyList()
        }
    }

    override fun generateProperties(
        callableId: CallableId,
        context: MemberGenerationContext?
    ): List<FirPropertySymbol> {
        val ownerKey = context?.let(::checkOwnerKey) ?: return emptyList()
        val owner = context.owner as? FirRegularClassSymbol ?: return emptyList()
        return when (callableId.callableName) {
            SERVICE_NAME -> createServiceName(callableId, owner, ownerKey)
            ENDPOINTS -> createEndpoints(callableId, owner, ownerKey)
            else -> emptyList()
        }
    }

    private fun createCreateStub(
        callableId: CallableId,
        owner: FirRegularClassSymbol,
        ownerKey: Key
    ): List<FirNamedFunctionSymbol> {
        // Obj<T>.createStub<S>(channel: SerializedService<S>): Service<T>
        val serviceClassId = owner.classId.outerClassId!!
        val objTypeParams = owner.typeParameterSymbols
        val retTypeProvider: (List<org.jetbrains.kotlin.fir.declarations.FirTypeParameter>) ->
        ConeKotlinType = { _ ->
            serviceClassId.createConeType(
                session,
                objTypeParams.map { it.toConeType() }.toTypedArray()
            )
        }
        val function = createMemberFunction(
            owner,
            ownerKey,
            callableId.callableName,
            retTypeProvider
        ) {
            modality = Modality.FINAL
            status {
                isOverride = true
            }
            typeParameter(Name.identifier("S"))
            valueParameter(Name.identifier("channel"), { types ->
                SERIALIZED_SERVICE.createConeType(session, arrayOf(types[0].toConeType()))
            })
        }
        return listOf(function.symbol)
    }

    private fun createFindEndpoint(
        callableId: CallableId,
        owner: FirRegularClassSymbol,
        ownerKey: Key
    ): List<FirNamedFunctionSymbol> {
        val retType =
            RPC_METHOD.createConeType(session, Array(3) { ConeStarProjection })
        val function = createMemberFunction(owner, ownerKey, callableId.callableName, retType) {
            modality = Modality.FINAL
            status {
                isOverride = true
            }
            valueParameter(Name.identifier("endpoint"), session.builtinTypes.stringType.coneType)
        }
        return listOf(function.symbol)
    }

    private fun createServiceName(
        callableId: CallableId,
        owner: FirRegularClassSymbol,
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
            status {
                isOverride = true
            }
        }
        return listOf(property.symbol)
    }

    private fun createEndpoints(
        callableId: CallableId,
        owner: FirRegularClassSymbol,
        ownerKey: Key
    ): List<FirPropertySymbol> {
        val listType = ClassId(
            FqName("kotlin.collections"),
            Name.identifier("List")
        ).createConeType(session, arrayOf(session.builtinTypes.stringType.coneType))
        val property = createMemberProperty(
            owner,
            ownerKey,
            callableId.callableName,
            listType,
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

    override fun getCallableNamesForClass(
        classSymbol: FirClassSymbol<*>,
        context: MemberGenerationContext
    ): Set<Name> {
        if (classSymbol.classKind != ClassKind.CLASS) return emptySet()
        val classId = (classSymbol as? FirRegularClassSymbol)?.classId ?: return emptySet()
        if (!classId.isNestedClass || classId.shortClassName != OBJ) return emptySet()
        val origin = classSymbol.origin as? FirDeclarationOrigin.Plugin
        return if (origin?.key is Key) {
            setOf(
                CREATE_STUB,
                FIND_ENDPOINT,
                SERVICE_NAME,
                ENDPOINTS,
                SpecialNames.INIT
            )
        } else {
            emptySet()
        }
    }

    override fun getNestedClassifiersNames(
        classSymbol: FirClassSymbol<*>,
        context: NestedClassGenerationContext
    ): Set<Name> {
        if (!session.predicateBasedProvider.matches(predicates, classSymbol)) return emptySet()
        val typeParams = (classSymbol as? FirRegularClassSymbol)?.typeParameterSymbols
        return if (typeParams.isNullOrEmpty()) {
            emptySet()
        } else {
            setOf(OBJ)
        }
    }

    data class Key(val target: String) : GeneratedDeclarationKey() {
        override fun toString(): String = "KsrpcObj($target)"
    }
}
