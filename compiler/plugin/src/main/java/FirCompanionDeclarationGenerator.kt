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
import com.monkopedia.ksrpc.plugin.FqConstants.KS_METHOD
import com.monkopedia.ksrpc.plugin.FqConstants.KS_SERVICE
import com.monkopedia.ksrpc.plugin.FqConstants.RPC_METHOD
import com.monkopedia.ksrpc.plugin.FqConstants.RPC_OBJECT
import com.monkopedia.ksrpc.plugin.FqConstants.SERIALIZED_SERVICE
import com.monkopedia.ksrpc.plugin.FqConstants.SERVICE_NAME
import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality.FINAL
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.builder.buildReceiverParameter
import org.jetbrains.kotlin.fir.declarations.utils.isSuspend
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
import org.jetbrains.kotlin.fir.scopes.impl.overrides
import org.jetbrains.kotlin.fir.scopes.impl.toConeType
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeStarProjection
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames

class FirCompanionDeclarationGenerator(session: FirSession) :
    FirDeclarationGenerationExtension(session) {

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
        if (name != SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT) return null
        return createCompanionObject(owner, Key(owner.classId)) {
            superType(RPC_OBJECT.createConeType(session, arrayOf(owner.defaultType())))
        }.symbol
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
        if (callableId.callableName == FqConstants.GET_INTROSPECTION) {
            return createIntrospectionStub(callableId, context?.owner)
        }
        val ownerKey = context?.let(::checkOwnerKey) ?: return emptyList()
        return when (callableId.callableName) {
            FIND_ENDPOINT -> createFindEndpoint(callableId, context.owner, ownerKey)
            CREATE_STUB -> createCreateStub(callableId, context.owner, ownerKey)
            else -> emptyList()
        }
    }

    override fun generateProperties(
        callableId: CallableId,
        context: MemberGenerationContext?
    ): List<FirPropertySymbol> {
        val ownerKey = context?.let(::checkOwnerKey) ?: return emptyList()
        return when (callableId.callableName) {
            SERVICE_NAME -> createServiceName(callableId, context.owner, ownerKey)
            ENDPOINTS -> createEndpoints(callableId, context.owner, ownerKey)
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

    private fun createIntrospectionStub(
        callableId: CallableId,
        owner: FirClassSymbol<*>?
    ): List<FirNamedFunctionSymbol> {
        val owner = owner ?: return emptyList()
        val retType = FqConstants.INTROSPECTION_SERVICE.createConeType(session, arrayOf())
        val function =
            createMemberFunction(
                owner,
                Key(owner.classId),
                callableId.callableName,
                retType
            ) {
                modality = FINAL
                this.visibility
                status {
                    this.isSuspend = true
                    this.isOverride = true
                }
                valueParameter(Name.identifier("u"), session.builtinTypes.unitType.coneType)
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
        val listType = ClassId(
            org.jetbrains.kotlin.name.FqName("kotlin.collections"),
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
            modality = FINAL
        }
        return listOf(property.symbol)
    }

    override fun getCallableNamesForClass(
        classSymbol: FirClassSymbol<*>,
        context: MemberGenerationContext
    ): Set<Name> {
        if (classSymbol.classKind != ClassKind.OBJECT) {
            if (session.predicateBasedProvider.matches(predicates, classSymbol)) {
                return setOf(FqConstants.GET_INTROSPECTION)
            }
            return emptySet()
        }
        (classSymbol as? FirRegularClassSymbol)?.classId
            ?.takeIf { it.isNestedClass }
            ?.takeIf { it.shortClassName == SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT }
            ?: return emptySet()

        val origin = classSymbol.origin as? FirDeclarationOrigin.Plugin
        return if (origin?.key is Key) {
            setOf(FIND_ENDPOINT, CREATE_STUB, SERVICE_NAME, ENDPOINTS, SpecialNames.INIT)
        } else {
            setOf(FIND_ENDPOINT, CREATE_STUB, SERVICE_NAME, ENDPOINTS)
        }
    }

    override fun getNestedClassifiersNames(
        classSymbol: FirClassSymbol<*>,
        context: NestedClassGenerationContext
    ): Set<Name> = if (session.predicateBasedProvider.matches(predicates, classSymbol)) {
        setOf(SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT)
    } else {
        emptySet()
    }

    data class Key(val classId: ClassId, val type: String = classId.asFqNameString()) :
        GeneratedDeclarationKey() {
        override fun toString(): String = "KsrpcService($type)"
    }
}
