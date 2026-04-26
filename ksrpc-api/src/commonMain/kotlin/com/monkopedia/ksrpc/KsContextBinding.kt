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
package com.monkopedia.ksrpc

import com.monkopedia.ksrpc.annotation.KsContext
import kotlin.coroutines.CoroutineContext

/**
 * Describes how a single piece of per-call context is propagated across the
 * wire. Implementations are referenced from [KsContext.binding] on a
 * meta-annotation and are responsible for:
 *
 *  - identifying the value on the wire ([wireKey]);
 *  - encoding / decoding the value for transports that carry it as a string
 *    ([toWire] / [fromWire]).
 *
 * The binding *is* the [CoroutineContext.Key] for its element — a single
 * source of truth for both the wire identity and the coroutine-context
 * identity. The recommended shape is to declare the binding as a named
 * companion object on the element type itself:
 *
 * ```
 * class AuthToken(val token: String) : CoroutineContext.Element {
 *     override val key get() = Key
 *
 *     companion object Key : KsContextBinding<AuthToken> {
 *         override val wireKey = "x-auth-token"
 *         override fun toWire(value: AuthToken) = value.token
 *         override fun fromWire(encoded: String) = AuthToken(encoded)
 *     }
 * }
 * ```
 *
 * Reference the binding at the annotation site by its companion name:
 *
 * ```
 * @KsContext(binding = AuthToken.Key::class)
 * ```
 *
 * Handlers then read the propagated value with the standard coroutine-context
 * lookup, naming the element type directly:
 *
 * ```
 * val token = coroutineContext[AuthToken]
 * ```
 *
 * The element type [T] must be a [CoroutineContext.Element] so it can sit
 * directly in the running coroutine's context.
 *
 * Implementations should be stateless and safe to reference from a
 * `KClass`-literal in an annotation argument.
 */
interface KsContextBinding<T : CoroutineContext.Element> : CoroutineContext.Key<T> {
    /**
     * Stable, transport-neutral identifier for this context entry. Transport
     * layers use this string to name the field that carries the encoded
     * value (e.g. as an HTTP header name, a JSON-RPC `meta` key, or a TLV
     * tag in the packet protocol).
     *
     * Two `@KsContext`-meta-annotated annotations applied to the same
     * `@KsMethod` whose bindings share a [wireKey] are rejected at compile
     * time.
     */
    val wireKey: String

    /** Encode a value of type [T] for transports that carry it as a string. */
    fun toWire(value: T): String

    /** Decode a value of type [T] from its [toWire] string representation. */
    fun fromWire(encoded: String): T
}
