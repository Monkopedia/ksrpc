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

import com.monkopedia.ksrpc.plugin.FqConstants.KS_INTROSPECTABLE
import com.monkopedia.ksrpc.plugin.FqConstants.KS_METHOD
import com.monkopedia.ksrpc.plugin.FqConstants.KS_SERVICE
import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.utils.isExpect
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.extensions.NestedClassGenerationContext
import org.jetbrains.kotlin.fir.extensions.predicate.LookupPredicate
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.plugin.ClassBuildingContext
import org.jetbrains.kotlin.fir.plugin.createConeType
import org.jetbrains.kotlin.fir.plugin.createConstructor
import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.fir.scopes.impl.toConeType
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeStarProjection
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames

class FirKsrpcStubGenerator(session: FirSession) : FirDeclarationGenerationExtension(session) {

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
        if (name != STUB) return null
        val classId = owner.classId.createNestedClassId(STUB)
        val serviceTypeParams = owner.typeParameterSymbols
        return ClassBuildingContext(
            session,
            Key(owner.classId.asFqNameString()),
            owner,
            classId,
            ClassKind.CLASS
        ).apply {
            modality = Modality.FINAL
            status {
                isExpect = owner.isExpect
            }
            for (tp in serviceTypeParams) {
                typeParameter(tp.name)
            }
            superType { refs ->
                if (serviceTypeParams.isEmpty()) {
                    owner.defaultType()
                } else {
                    owner.classId.createConeType(
                        session,
                        refs.map { it.symbol.toConeType() }.toTypedArray()
                    )
                }
            }
            superType(RPC_SERVICE.createConeType(session))
        }.build().symbol
    }

    private fun checkOwnerKey(context: MemberGenerationContext): Key? =
        (context.owner.origin as? FirDeclarationOrigin.Plugin)?.key as? Key

    override fun generateConstructors(
        context: MemberGenerationContext
    ): List<FirConstructorSymbol> {
        val ownerKey = checkOwnerKey(context) ?: return emptyList()
        val owner = context.owner as FirRegularClassSymbol
        // The service's type parameters show up on the Stub as its own type parameters, in
        // the same order. If the owning service is generic, the primary constructor takes
        // one KSerializer<T> per type parameter in addition to the channel.
        val stubTypeParams = owner.typeParameterSymbols
        val constructor = createConstructor(owner, ownerKey, isPrimary = true) {
            valueParameter(
                CHANNEL,
                SERIALIZED_SERVICE.createConeType(session, arrayOf(ConeStarProjection))
            )
            for ((idx, tp) in stubTypeParams.withIndex()) {
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

    override fun getCallableNamesForClass(
        classSymbol: FirClassSymbol<*>,
        context: MemberGenerationContext
    ): Set<Name> {
        if (classSymbol.classKind != ClassKind.CLASS) return emptySet()
        (classSymbol as? FirRegularClassSymbol)
            ?.classId
            ?.takeIf { it.isNestedClass }
            ?.takeIf { it.shortClassName == STUB }
            ?: return emptySet()
        val origin = classSymbol.origin as? FirDeclarationOrigin.Plugin
        return if (origin?.key is Key) {
            setOf(SpecialNames.INIT)
        } else {
            setOf()
        }
    }

    override fun getNestedClassifiersNames(
        classSymbol: FirClassSymbol<*>,
        context: NestedClassGenerationContext
    ): Set<Name> = if (session.predicateBasedProvider.matches(predicates, classSymbol)) {
        setOf(STUB)
    } else {
        emptySet()
    }

    data class Key(val target: String) : GeneratedDeclarationKey() {
        override fun toString(): String = "KsrpcStub($target)"
    }

    companion object {
        val STUB = Name.identifier("Stub")
        val CHANNEL = Name.identifier("channel")
        val RPC_SERVICE = ClassId(FqName("com.monkopedia.ksrpc"), Name.identifier("RpcService"))
        val SERIALIZED_SERVICE =
            ClassId(FqName("com.monkopedia.ksrpc.channels"), Name.identifier("SerializedService"))
        val KSERIALIZER =
            ClassId(FqName("kotlinx.serialization"), Name.identifier("KSerializer"))
    }
}
