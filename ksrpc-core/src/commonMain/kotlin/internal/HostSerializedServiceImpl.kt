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
package com.monkopedia.ksrpc.internal

import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.RpcEndpointNotFoundException
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
import kotlinx.serialization.builtins.serializer

class HostSerializedChannelImpl<T>(
    override val env: KsrpcEnvironment<T>,
    channelContext: CoroutineContext? = null
) : Connection<T> {
    private var baseChannel = CompletableDeferred<SerializedService<T>>()
    override val context: CoroutineContext = channelContext
        ?: (ClientChannelContext(this) + HostChannelContext(this) + env.coroutineExceptionHandler)
    private val onCloseObservers = mutableSetOf<suspend () -> Unit>()

    private val serviceMap by lazy {
        mutableMapOf<String, SerializedService<T>>()
    }

    override suspend fun call(
        channelId: ChannelId,
        endpoint: String,
        data: CallData<T>
    ): CallData<T> = try {
        val channel = if (channelId.id.isEmpty()) {
            baseChannel.await()
        } else {
            serviceMap[channelId.id] ?: error("Cannot find service ${channelId.id}")
        }
        withContext(context) {
            if (endpoint.isEmpty()) {
                close(channelId)
                env.serialization.createCallData(Unit.serializer(), Unit)
            } else {
                channel.call(endpoint, data)
            }
        }
    } catch (t: Throwable) {
        env.logger.info("SerializedChannel", "Exception thrown during dispatching", t)
        env.errorListener.onError(t)
        val failure = RpcFailure(t.asString)
        if (t is RpcEndpointNotFoundException) {
            env.serialization.createEndpointNotFoundCallData(RpcFailure.serializer(), failure)
        } else {
            env.serialization.createErrorCallData(RpcFailure.serializer(), failure)
        }
    }

    override suspend fun close(id: ChannelId) {
        env.logger.debug("SerializedChannel", "Closing channel ${id.id}")
        serviceMap.remove(id.id)?.let {
            it.trackingService?.onSerializationClosed(it)
            it.close()
        }
    }

    override suspend fun close() {
        env.logger.debug("SerializedChannel", "Closing entire channel")
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

    override suspend fun registerDefault(service: SerializedService<T>) {
        baseChannel.complete(service)
        service.trackingService?.onSerializationCreated(service)
    }

    override suspend fun registerHost(service: SerializedService<T>): ChannelId {
        val serviceId = ChannelId(randomUuid())
        env.logger.debug("SerializedChannel", "Registered host service ${serviceId.id}")
        serviceMap[serviceId.id] = service
        service.trackingService?.onSerializationCreated(service)
        return serviceId
    }

    override suspend fun wrapChannel(channelId: ChannelId): SerializedService<T> {
        env.logger.debug("SerializedChannel", "Wrapping (unmapping) local channel ${channelId.id}")
        return serviceMap[channelId.id] ?: error("Unknown service ${channelId.id}")
    }

    private val SerializedService<T>.trackingService: TrackingService?
        get() = (this as? HostSerializedServiceImpl<*, T>)?.service as? TrackingService
}

val <T> SerializedChannel<T>.asClient: ChannelClient<T>
    get() = object : ChannelClient<T>, SerializedChannel<T> by this {
        override suspend fun wrapChannel(channelId: ChannelId): SerializedService<T> {
            env.logger.debug("SerializedChannel", "Wrapping channel ${channelId.id}")
            return SubserviceChannel(this, channelId)
        }
    }

internal class HostSerializedServiceImpl<T : RpcService, S>(
    internal val service: T,
    private val rpcObject: RpcObject<T>,
    override val env: KsrpcEnvironment<S>
) : SerializedService<S> {
    private val onCloseCallbacks = mutableSetOf<suspend () -> Unit>()

    override suspend fun call(endpoint: String, input: CallData<S>): CallData<S> {
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
