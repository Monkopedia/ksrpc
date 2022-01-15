/*
 * Copyright 2021 Jason Monk
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

import kotlinx.serialization.json.Json

interface RpcService : SuspendCloseable {
    override suspend fun close() = Unit
}

interface RpcObject<T : RpcService> {
    fun createStub(channel: SerializedService): T
    fun findEndpoint(endpoint: String): RpcMethod<*, *, *>
}

expect inline fun <reified T : RpcService> rpcObject(): RpcObject<T>

inline fun <reified T : RpcService> T.serialized(
    errorListener: ErrorListener = ErrorListener { },
    json: Json = Json { isLenient = true }
): SerializedService {
    return serialized(rpcObject(), errorListener, json)
}

inline fun <reified T : RpcService> SerializedService.toStub(): T {
    return rpcObject<T>().createStub(this)
}

class RpcEndpointException(str: String) : IllegalArgumentException(str)
