/**
 * Copyright (C) 2024 Jason Monk <monkopedia@gmail.com>
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

import com.monkopedia.ksrpc.channels.SerializedService
import com.monkopedia.ksrpc.internal.HostSerializedServiceImpl

/**
 * Super-interface of all services tagged with [KsService].
 */
interface RpcService : SuspendCloseable {
    override suspend fun close() = Unit
}

/**
 * Interface for generated companions of [RpcService].
 */
interface RpcObject<T : RpcService> {
    fun <S> createStub(channel: SerializedService<S>): T
    fun findEndpoint(endpoint: String): RpcMethod<*, *, *>
}

/**
 * Helper to get [RpcObject] for a given [RpcService]
 */
expect inline fun <reified T : RpcService> rpcObject(): RpcObject<T>

/**
 * Convert a [T] into a [SerializedService] for hosting.
 */
inline fun <reified T : RpcService, S> T.serialized(
    env: KsrpcEnvironment<S>
): SerializedService<S> {
    return serialized(rpcObject(), env)
}

/**
 * Convert a [T] into a [SerializedService] for hosting.
 */
fun <T : RpcService, S> T.serialized(
    rpcObject: RpcObject<T>,
    env: KsrpcEnvironment<S>
): SerializedService<S> {
    val rpcChannel = this
    return HostSerializedServiceImpl(rpcChannel, rpcObject, env)
}

/**
 * Convert a [SerializedService] to a [T] for use as a client.
 */
inline fun <reified T : RpcService, S> SerializedService<S>.toStub(): T {
    return rpcObject<T>().createStub(this)
}

/**
 * Thrown when an endpoint cannot be found.
 * Could happen from version mismatch or other programmer errors.
 */
class RpcEndpointException(str: String) : IllegalArgumentException(str)
