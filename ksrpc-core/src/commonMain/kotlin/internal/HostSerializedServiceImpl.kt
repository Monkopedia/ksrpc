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

import com.monkopedia.ksrpc.ERROR_PREFIX
import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.RpcFailure
import com.monkopedia.ksrpc.RpcObject
import com.monkopedia.ksrpc.RpcService
import com.monkopedia.ksrpc.SuspendCloseable
import com.monkopedia.ksrpc.TrackingService
import com.monkopedia.ksrpc.asString
import com.monkopedia.ksrpc.channels.CallData
import com.monkopedia.ksrpc.channels.ChannelClient
import com.monkopedia.ksrpc.channels.ChannelId
import com.monkopedia.ksrpc.channels.Connection
import com.monkopedia.ksrpc.channels.SerializedChannel
import com.monkopedia.ksrpc.channels.SerializedService
import com.monkopedia.ksrpc.channels.randomUuid
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withContext

class HostSerializedChannelImpl(
    override val env: KsrpcEnvironment,
    channelContext: CoroutineContext? = null
) : Connection {
    private var baseChannel = CompletableDeferred<SerializedService>()
    override val context: CoroutineContext = channelContext
        ?: (ClientChannelContext(this) + HostChannelContext(this) + env.coroutineExceptionHandler)
    private val onCloseObservers = mutableSetOf<suspend () -> Unit>()

    private val serviceMap by lazy {
        mutableMapOf<String, SerializedService>()
    }

    override suspend fun call(channelId: ChannelId, endpoint: String, data: CallData): CallData {
        return try {
            val channel = if (channelId.id.isEmpty()) {
                baseChannel.await()
            } else {
                serviceMap[channelId.id] ?: error("Cannot find service ${channelId.id}")
            }
            withContext(context) {
                if (endpoint.isEmpty()) {
                    close(channelId)
                    CallData.create("{}")
                } else {
                    channel.call(endpoint, data)
                }
            }
        } catch (t: Throwable) {
            env.errorListener.onError(t)
            CallData.create(
                ERROR_PREFIX + env.serialization.encodeToString(
                    RpcFailure.serializer(),
                    RpcFailure(t.asString)
                )
            )
        }
    }

    override suspend fun close(id: ChannelId) {
        serviceMap.remove(id.id)?.let {
            it.trackingService?.onSerializationClosed(it)
            it.close()
        }
    }

    override suspend fun close() {
        serviceMap.values.forEach {
            it.trackingService?.onSerializationClosed(it)
            it.close()
        }
        serviceMap.clear()
        onCloseObservers.forEach { it.invoke() }
    }

    override suspend fun onClose(onClose: suspend () -> Unit) {
        onCloseObservers.add(onClose)
    }

    override suspend fun registerDefault(channel: SerializedService) {
        baseChannel.complete(channel)
        channel.trackingService?.onSerializationCreated(channel)
    }

    override suspend fun registerHost(channel: SerializedService): ChannelId {
        val serviceId = ChannelId(randomUuid())
        serviceMap[serviceId.id] = channel
        channel.trackingService?.onSerializationCreated(channel)
        return serviceId
    }

    override suspend fun wrapChannel(channelId: ChannelId): SerializedService {
        return serviceMap[channelId.id] ?: error("Unknown service ${channelId.id}")
    }

    private val SerializedService.trackingService: TrackingService?
        get() = (this as? HostSerializedServiceImpl<*>)?.service as? TrackingService
}

val SerializedChannel.asClient: ChannelClient
    get() = object : ChannelClient, SerializedChannel by this {
        override suspend fun wrapChannel(channelId: ChannelId): SerializedService {
            return SubserviceChannel(this, channelId)
        }
    }

internal class HostSerializedServiceImpl<T : RpcService>(
    internal val service: T,
    private val rpcObject: RpcObject<T>,
    override val env: KsrpcEnvironment
) : SerializedService {
    private val onCloseCallbacks = mutableSetOf<suspend () -> Unit>()

    override suspend fun call(endpoint: String, input: CallData): CallData {
        val rpcEndpoint = rpcObject.findEndpoint(endpoint)
        return rpcEndpoint.call(this, service, input)
    }

    override suspend fun onClose(onClose: suspend () -> Unit) {
        onCloseCallbacks.add(onClose)
    }

    override suspend fun close() {
        (service as? SuspendCloseable)?.close()
        onCloseCallbacks.forEach { it.invoke() }
    }
}