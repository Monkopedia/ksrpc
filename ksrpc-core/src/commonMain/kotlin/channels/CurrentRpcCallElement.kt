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

import com.monkopedia.ksrpc.RpcMethod
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * Coroutine-context element describing the RPC call currently being handled by a ksrpc
 * dispatcher. Installed at the single chokepoint where the user's handler body is invoked
 * ([RpcMethod.call]) so that the handler can introspect its own call identity (for correlating
 * progress notifications, streaming side-channels, etc.) via [currentRpcCall].
 *
 * Because the element is (re)installed at each handler invocation, nested outbound calls
 * naturally see a fresh element installed at the destination's [RpcMethod.call] — no
 * caller-side stripping is required.
 */
class CurrentRpcCallElement(
    /**
     * The [RpcMethod] being handled. Handlers can read its [RpcMethod.endpoint],
     * [RpcMethod.metadata], etc. Not nullable — the element is only installed when a concrete
     * method is being dispatched.
     */
    val method: RpcMethod<*, *, *>,
    /**
     * Transport-specific identifier for this call, or `null` for calls with no id on the wire
     * (e.g. a JSON-RPC notification) and for in-process handler invocations that bypass a
     * transport.
     */
    val id: RpcCallId?
) : AbstractCoroutineContextElement(CurrentRpcCall) {
    override fun toString(): String = "CurrentRpcCallElement(method=${method.endpoint}, id=$id)"

    /** Key for [CurrentRpcCallElement] in a [CoroutineContext]. */
    companion object CurrentRpcCall : CoroutineContext.Key<CurrentRpcCallElement>
}

/**
 * Returns the [CurrentRpcCallElement] installed by the dispatcher for the currently executing
 * handler, or `null` when not running inside an RPC handler.
 */
suspend fun currentRpcCall(): CurrentRpcCallElement? = coroutineContext[CurrentRpcCallElement]
