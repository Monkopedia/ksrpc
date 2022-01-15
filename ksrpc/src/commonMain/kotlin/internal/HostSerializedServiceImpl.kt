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
import com.monkopedia.ksrpc.ChannelClient
import com.monkopedia.ksrpc.ChannelHost
import com.monkopedia.ksrpc.ChannelHostProvider
import com.monkopedia.ksrpc.ChannelId
import com.monkopedia.ksrpc.ERROR_PREFIX
import com.monkopedia.ksrpc.ErrorListener
import com.monkopedia.ksrpc.RpcFailure
import com.monkopedia.ksrpc.RpcObject
import com.monkopedia.ksrpc.RpcService
import com.monkopedia.ksrpc.SerializedChannel
import com.monkopedia.ksrpc.SerializedService
import com.monkopedia.ksrpc.SuspendCloseable
import com.monkopedia.ksrpc.asString
import com.monkopedia.ksrpc.randomUuid
import io.ktor.utils.io.core.Closeable
import kotlinx.serialization.StringFormat

internal class HostSerializedChannelImpl(
    private val errorListener: ErrorListener,
    override val serialization: StringFormat
) : ChannelHost, SerializedChannel {
    private var baseChannel: SerializedService? = null

    private val serviceMap by lazy {
        mutableMapOf<String, SerializedService>()
    }

    override suspend fun call(channelId: ChannelId, endpoint: String, data: CallData): CallData {
        return try {
            val channel = if (channelId.id.isEmpty()) {
                baseChannel ?: error("No default service has been specified")
            } else {
                serviceMap[channelId.id] ?: error("Cannot find service ${channelId.id}")
            }
            (channel as? HostSerializedServiceImpl<*>)?.host = this
            channel.call(endpoint, data)
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

    override suspend fun close(id: ChannelId) {
        serviceMap.remove(id.id)?.close()
    }

    override suspend fun close() {
        serviceMap.values.forEach {
            it.close()
        }
        serviceMap.clear()
    }

    override fun registerDefault(channel: SerializedService) {
        baseChannel = channel
    }

    override fun registerHost(channel: SerializedService): ChannelId {
        val serviceId = ChannelId(randomUuid())
        serviceMap[serviceId.id] = channel
        return serviceId
    }
}

internal val SerializedChannel.asClient: ChannelClient
    get() = object : ChannelClient, SerializedChannel by this {
        override fun wrapChannel(channelId: ChannelId): SerializedService {
            return SubserviceChannel(this, channelId)
        }
    }

internal class HostSerializedServiceImpl<T : RpcService>(
    private val service: T,
    private val rpcObject: RpcObject<T>,
    override val serialization: StringFormat
) : SerializedService, ChannelHostProvider {
    override var host: ChannelHost? = null
        internal set

    override suspend fun call(endpoint: String, input: CallData): CallData {
        val rpcEndpoint = rpcObject.findEndpoint(endpoint)
        return rpcEndpoint.call(this, service, input)
    }

    override suspend fun close() {
        (service as? Closeable)?.close() ?: (service as? SuspendCloseable)?.close()
    }
}
