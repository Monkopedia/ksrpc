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
import com.monkopedia.ksrpc.plugin.FqConstants.KS_INTROSPECTABLE
import com.monkopedia.ksrpc.plugin.FqConstants.KS_METHOD
import com.monkopedia.ksrpc.plugin.FqConstants.KS_SERVICE
import com.monkopedia.ksrpc.plugin.FqConstants.SERVICE_NAME
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
import org.jetbrains.kotlin.fir.plugin.createConeType
import org.jetbrains.kotlin.fir.plugin.createMemberFunction
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames

class FirKsrpcIntrospectionGenerator(session: FirSession) :
    FirDeclarationGenerationExtension(session) {

    private val introspectPredicates =
        listOf(KS_INTROSPECTABLE).map { LookupPredicate.create { annotated(it) } }

    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        register(introspectPredicates)
    }

    private fun checkOwnerKey(context: MemberGenerationContext): Key? =
        (context.owner.origin as? FirDeclarationOrigin.Plugin)?.key as? Key

    override fun generateFunctions(
        callableId: CallableId,
        context: MemberGenerationContext?
    ): List<FirNamedFunctionSymbol> {
        if (callableId.callableName != FqConstants.GET_INTROSPECTION) return emptyList()
        if (context?.owner?.isIntrospectable() != true) return emptyList()
        return createIntrospectionStub(callableId, context.owner)
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

    override fun getCallableNamesForClass(
        classSymbol: FirClassSymbol<*>,
        context: MemberGenerationContext
    ): Set<Name> {
        if (classSymbol.classKind == ClassKind.OBJECT ||
            !session.predicateBasedProvider.matches(introspectPredicates, classSymbol)
        ) {
            return emptySet()
        }
        if (classSymbol.classId == FqConstants.INTROSPECTABLE_RPC_SERVICE) return emptySet()
        return setOf(FqConstants.GET_INTROSPECTION)
    }

    private fun FirClassSymbol<*>?.isIntrospectable(): Boolean =
        this?.resolvedSuperTypes?.any { type ->
            (type as? ConeClassLikeType)?.lookupTag?.classId ==
                FqConstants.INTROSPECTABLE_RPC_SERVICE
        } == true

    data class Key(val classId: ClassId, val type: String = classId.asFqNameString()) :
        GeneratedDeclarationKey() {
        override fun toString(): String = "KsrpcIntroService($type)"
    }
}
