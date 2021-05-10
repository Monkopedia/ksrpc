/*
 * Copyright 2020 Jason Monk
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

import io.ktor.utils.io.ByteReadChannel
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

interface SerializedChannel : SuspendCloseable {
    suspend fun call(endpoint: String, input: String): String
    suspend fun callBinary(endpoint: String, input: String): ByteReadChannel
    suspend fun callBinaryInput(endpoint: String, input: ByteReadChannel): String
}

expect fun randomUuid(): String

fun <T : RpcService> RpcChannel.serialized(
    rpcObject: RpcObject<T>,
    errorListener: ErrorListener = ErrorListener { },
    json: Json = Json { isLenient = true },
): SerializedChannel {
    val rpcChannel = this
    return SerializedChannelImpl(rpcChannel, rpcObject, errorListener, json)
}

private class SerializedChannelImpl<T : RpcService>(
    private val rpcChannel: RpcChannel,
    private val rpcObject: RpcObject<T>,
    private val errorListener: ErrorListener,
    private val json: Json,
) : SerializedChannel {
    private val serviceMap by lazy {
        mutableMapOf<String, SerializedChannelImpl<*>>()
    }

    fun findService(target: List<String>): SerializedChannelImpl<*> {
        if (target.isEmpty()) return this
        val service = serviceMap[target.first()] ?: error("Unrecognized service $target")
        return service.findService(target.subList(1, target.size))
    }

    private suspend fun close(last: String) {
        serviceMap.remove(last)?.close()
    }

    override suspend fun call(endpoint: String, input: String): String {
        return try {
            val (endpoint, services) = json.decodedEndpoint(endpoint)
            if (services != null && services.isNotEmpty()) {
                if (services.first() == "close" && endpoint.isEmpty()) {
                    val service = findService(services.subList(1, services.size - 1))
                    service.close(services.last())
                    return json.encodeToString(Unit.serializer(), Unit)
                }
                val service = findService(services)
                return service.call(endpoint, input)
            }
            val rpcEndpoint = rpcObject.info.findEndpoint(endpoint)
                as RpcServiceInfoBase.RpcEndpoint<T, Any?, Any?>
            val (_, isService, inputSer, outputSer) = rpcEndpoint
            val output = rpcChannel.call(
                endpoint,
                inputSer,
                outputSer,
                if (input.isNotEmpty()) json.decodeFromString(inputSer, input) else null
            )
            if (isService) {
                val serviceId = randomUuid()
                val service = rpcEndpoint.subservice as RpcObject<RpcService>
                serviceMap[serviceId] =
                    SerializedChannelImpl(
                        service.channel(output as RpcService), service,
                        errorListener, json
                    )
                json.encodeToString(String.serializer(), serviceId)
            } else {
                json.encodeToString(outputSer, output)
            }
        } catch (t: Throwable) {
            errorListener.onError(t)
            ERROR_PREFIX + json.encodeToString(RpcFailure.serializer(), RpcFailure(t.asString))
        }
    }

    override suspend fun callBinary(endpoint: String, input: String): ByteReadChannel {
        return try {
            val (endpoint, services) = json.decodedEndpoint(endpoint)
            if (services != null && services.isNotEmpty()) {
                val service = findService(services)
                return service.callBinary(endpoint, input)
            }
            val rpcEndpoint = rpcObject.info.findEndpoint(endpoint)
                as RpcServiceInfoBase.RpcEndpoint<T, Any?, Any?>
            val (_, _, inputSer, _) = rpcEndpoint
            rpcChannel.callBinary(
                endpoint,
                inputSer,
                if (input.isNotEmpty()) json.decodeFromString(inputSer, input) else null
            )
        } catch (t: Throwable) {
            errorListener.onError(t)
            throw t
        }
    }

    override suspend fun callBinaryInput(endpoint: String, input: ByteReadChannel): String {
        return try {
            val (endpoint, services) = json.decodedEndpoint(endpoint)
            if (services != null && services.isNotEmpty()) {
                val service = findService(services)
                return service.callBinaryInput(endpoint, input)
            }
            val rpcEndpoint = rpcObject.info.findEndpoint(endpoint)
                as RpcServiceInfoBase.RpcEndpoint<T, Any?, Any?>
            val (_, _, _, outputSer) = rpcEndpoint
            json.encodeToString(
                outputSer,
                rpcChannel.callBinaryInput(
                    endpoint,
                    outputSer,
                    input
                )
            )
        } catch (t: Throwable) {
            errorListener.onError(t)
            ERROR_PREFIX + json.encodeToString(RpcFailure.serializer(), RpcFailure(t.asString))
        }
    }

    override suspend fun close() {
        rpcChannel.close()
    }
}

fun <T : RpcService> RpcObject<T>.serializedChannel(
    service: T,
    errorListener: ErrorListener = ErrorListener { },
): SerializedChannel {
    return info.createChannelFor(service).serialized(this, errorListener = errorListener)
}
