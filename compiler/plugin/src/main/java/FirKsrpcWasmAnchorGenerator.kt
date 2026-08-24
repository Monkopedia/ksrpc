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
@file:OptIn(ExperimentalTopLevelDeclarationsGenerationApi::class)

package com.monkopedia.ksrpc.plugin

import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.ExperimentalTopLevelDeclarationsGenerationApi
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.extensions.predicate.LookupPredicate
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.plugin.createTopLevelProperty
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.wasm.isWasmJs

/**
 * Declares one private top-level `val` per `@KsService`, holding a reference to that
 * service's generated `Stub` class. [WasmAnchorGeneration] fills in the initializer and
 * marks the property `@EagerInitialization`.
 *
 * Kotlin/Wasm resolves `findAssociatedObject` — how `rpcObject<T>()` finds a service's
 * companion on wasm — through a table that, under production-mode compilation, only
 * retains an entry for a service type that has a reachable implementor. A service a
 * module only *consumes* has none: the generated `Stub` is never instantiated, so the
 * entry is dropped and `rpcObject<T>()` fails at runtime with "Can't find rpc companion".
 * That is the ordinary client-only shape, and it affects published `-wasm-js` artifacts
 * (issue #258).
 *
 * An eagerly-initialized reference to `Stub::class` is enough to keep the entry. It costs
 * one `KClass` reference per service at module load and does not instantiate the Stub.
 * Referencing the companion instead does *not* work: the table keys on the service type
 * having an implementor, not on the associated object being alive.
 *
 * Only generated for wasm-js. Kotlin/JS resolves the same call site correctly in every
 * configuration measured, and the JVM and native `rpcObject` implementations don't use
 * associated objects at all.
 */
class FirKsrpcWasmAnchorGenerator(session: FirSession) :
    FirDeclarationGenerationExtension(session) {

    private val servicePredicate = LookupPredicate.create { annotated(FqConstants.KS_SERVICE) }

    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        register(servicePredicate)
    }

    /** Anchor property id -> the service it keeps resolvable. Empty off wasm-js. */
    private val anchors: Map<CallableId, ClassId> by lazy {
        if (!session.moduleData.platform.isWasmJs()) return@lazy emptyMap()
        session.predicateBasedProvider.getSymbolsByPredicate(servicePredicate)
            .filterIsInstance<FirRegularClassSymbol>()
            .associate { anchorId(it.classId) to it.classId }
    }

    override fun getTopLevelCallableIds(): Set<CallableId> = anchors.keys

    override fun generateProperties(
        callableId: CallableId,
        context: MemberGenerationContext?
    ): List<FirPropertySymbol> {
        if (context != null) return emptyList()
        val service = anchors[callableId] ?: return emptyList()
        val property = createTopLevelProperty(
            Key(service),
            callableId,
            session.builtinTypes.anyType.coneType
        ) {
            visibility = Visibilities.Private
        }
        return listOf(property.symbol)
    }

    data class Key(val classId: ClassId, val type: String = classId.asFqNameString()) :
        GeneratedDeclarationKey() {
        override fun toString(): String = "KsrpcWasmAnchor($type)"
    }

    companion object {
        /**
         * Nested services flatten their relative name, so `Outer.Inner` and a top-level
         * `Outer_Inner` can't collide within a package.
         */
        fun anchorId(service: ClassId): CallableId = CallableId(
            service.packageFqName,
            Name.identifier(
                service.relativeClassName.pathSegments()
                    .joinToString(separator = "_", prefix = "ksrpcWasmAnchor_") { it.asString() }
            )
        )
    }
}
