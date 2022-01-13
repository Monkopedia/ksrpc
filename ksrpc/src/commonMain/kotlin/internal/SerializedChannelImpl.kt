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
package com.monkopedia.ksrpc.internal

import com.monkopedia.ksrpc.CallData
import com.monkopedia.ksrpc.ERROR_PREFIX
import com.monkopedia.ksrpc.ErrorListener
import com.monkopedia.ksrpc.HostingSerializedChannel
import com.monkopedia.ksrpc.RpcFailure
import com.monkopedia.ksrpc.RpcObject
import com.monkopedia.ksrpc.RpcService
import com.monkopedia.ksrpc.SerializedChannel
import com.monkopedia.ksrpc.asString
import com.monkopedia.ksrpc.decodedEndpoint
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

internal class SerializedChannelImpl<T : RpcService>(
    private val service: T,
    private val rpcObject: RpcObject<T>,
    private val errorListener: ErrorListener,
    override val serialization: Json
) : SerializedChannel, HostingSerializedChannel {
    private val serviceMap by lazy {
        mutableMapOf<String, SerializedChannelImpl<*>>()
    }

    fun findService(target: List<String>): SerializedChannelImpl<*> {
        if (target.isEmpty()) return this
        val service = serviceMap[target.first()]
            ?: error("Unrecognized service $target in $serviceMap")
        return service.findService(target.subList(1, target.size))
    }

    private suspend fun close(last: String) {
        serviceMap.remove(last)?.close()
    }

    override fun <T : RpcService> registerSubService(
        serviceId: String,
        service: T,
        obj: RpcObject<T>
    ) {
        serviceMap[serviceId] =
            SerializedChannelImpl(service, obj, errorListener, serialization)
    }

    override suspend fun call(endpoint: String, input: CallData): CallData {
        return try {
            val (endpoint, services) = serialization.decodedEndpoint(endpoint)
            if (services != null && services.isNotEmpty()) {
                if (services.first() == "close" && endpoint.isEmpty()) {
                    val service = findService(services.subList(1, services.size - 1))
                    service.close(services.last())
                    return CallData.create(serialization.encodeToString(Unit.serializer(), Unit))
                }
                val service = findService(services)
                return service.call(endpoint, input)
            }
            val rpcEndpoint = rpcObject.findEndpoint(endpoint)
            return rpcEndpoint.call(this, service, input)
        } catch (t: Throwable) {
            errorListener.onError(t)
            CallData.create(
                ERROR_PREFIX + serialization.encodeToString(
                    RpcFailure.serializer(),
                    RpcFailure(t.asString)
                )
            )
        }
    }

    override suspend fun close() {
    }
}
