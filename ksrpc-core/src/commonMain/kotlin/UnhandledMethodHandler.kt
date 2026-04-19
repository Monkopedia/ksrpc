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

import com.monkopedia.ksrpc.channels.CallData

/**
 * Opt-in fallback for services that want to handle incoming RPC calls targeting
 * method names that are not registered on the service.
 *
 * An [RpcService] may implement this interface in addition to its normal
 * [com.monkopedia.ksrpc.annotation.KsMethod]-annotated endpoints. When a call arrives
 * for an endpoint that [RpcObject.findEndpoint] does not recognize, ksrpc will route
 * the call to [onUnhandled] instead of immediately returning an
 * [RpcEndpointException] to the caller.
 *
 * The handler receives the raw [CallData] payload for the call and must return a
 * [CallData] payload to be serialized back to the caller. This type is transport
 * agnostic — it's the same envelope that core's dispatch path passes around for
 * every call, so implementations work unchanged across every ksrpc transport
 * (JSON-RPC, packet-framed, JNI, etc.).
 *
 * Exceptions thrown from [onUnhandled] are reported back to the caller as remote
 * errors, identical to the path taken when a normal handler throws.
 *
 * This is a receiver-side fallback: senders dispatch exactly as they do today.
 * Services that do not implement this interface preserve the existing
 * unknown-endpoint error behavior.
 */
interface UnhandledMethodHandler<T> {
    /**
     * Invoked when an incoming call targets an endpoint that is not registered on
     * this service.
     *
     * @param method The endpoint name the caller requested (with any leading `/`
     *   stripped, matching how registered endpoints are stored).
     * @param input The serialized call payload as it arrived on the wire.
     * @return The serialized payload to return to the caller.
     */
    suspend fun onUnhandled(method: String, input: CallData<T>): CallData<T>
}
