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
package com.monkopedia.ksrpc.channels

import com.monkopedia.ksrpc.annotation.KsrpcInternal
import kotlin.coroutines.CoroutineContext

/**
 * Coroutine-context element that carries wire-encoded `@KsContext` values
 * through the call chain. The stub-side [com.monkopedia.ksrpc.RpcMethod.callChannel]
 * builds a [WireContextMap] from the caller's coroutine context (encoding each
 * present [com.monkopedia.ksrpc.KsContextBinding] via `toWire`) and installs it
 * so transports can read the values and propagate them across the wire.
 *
 * On the server side, the transport installs a [WireContextMap] extracted from
 * the incoming wire frame. [com.monkopedia.ksrpc.RpcMethod.call] then decodes
 * each entry via `fromWire` and installs real [CoroutineContext.Element]s so
 * handlers see them via the standard `coroutineContext[MyElement]` lookup.
 *
 * This class is transport plumbing and not part of the public API.
 */
@KsrpcInternal
class WireContextMap(val values: Map<String, String>) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> get() = Key

    companion object Key : CoroutineContext.Key<WireContextMap>
}

/**
 * An [RpcCallId] wrapper that carries a [WireContextMap] alongside the
 * original (possibly null) call id. Used by [com.monkopedia.ksrpc.RpcMethod.callChannel]
 * to forward wire-encoded context values through the call chain without
 * depending on coroutine context propagation, which may not survive through
 * intermediate `withContext` switches in the in-process channel path.
 */
@KsrpcInternal
class WireContextCallId(
    val delegate: RpcCallId?,
    val wireContextMap: WireContextMap?
) : RpcCallId
