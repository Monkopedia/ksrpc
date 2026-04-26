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
@file:OptIn(KsrpcInternal::class)

package com.monkopedia.ksrpc.internal

import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.KsrpcException
import com.monkopedia.ksrpc.RpcEndpointException
import com.monkopedia.ksrpc.RpcObject
import com.monkopedia.ksrpc.RpcService
import com.monkopedia.ksrpc.SuspendCloseable
import com.monkopedia.ksrpc.TrackingService
import com.monkopedia.ksrpc.UnhandledMethodHandler
import com.monkopedia.ksrpc.annotation.KsrpcInternal
import com.monkopedia.ksrpc.asString
import com.monkopedia.ksrpc.channels.CallData
import com.monkopedia.ksrpc.channels.ChannelClient
import com.monkopedia.ksrpc.channels.ChannelId
import com.monkopedia.ksrpc.channels.Connection
import com.monkopedia.ksrpc.channels.RpcCallId
import com.monkopedia.ksrpc.channels.SerializedChannel
import com.monkopedia.ksrpc.channels.SerializedService
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.serializer

@KsrpcInternal
class HostSerializedChannelImpl<T>(
    override val env: KsrpcEnvironment<T>,
    channelContext: CoroutineContext? = null
) : Connection<T> {
    private var baseChannel = CompletableDeferred<SerializedService<T>>()
    override val context: CoroutineContext = channelContext
        ?: (ClientChannelContext(this) + HostChannelContext(this) + env.coroutineExceptionHandler)
    private val onCloseObservers = mutableSetOf<suspend () -> Unit>()
    private var isClosed: Boolean = false

    private val serviceMap by lazy {
        mutableMapOf<String, SerializedService<T>>()
    }

    override suspend fun call(
        channelId: ChannelId,
        endpoint: String,
        data: CallData<T>,
        callId: RpcCallId?
    ): CallData<T> = try {
        val channel = if (channelId.id.isEmpty()) {
            baseChannel.await()
        } else {
            serviceMap[channelId.id] ?: throw RpcEndpointException(
                "Cannot find service ${channelId.id}"
            )
        }
        // [context] is stored on this channel and typically carries the channel's Job — we
        // strip it so cancellation from the caller (the handler coroutine on the server, or
        // the stub invoker on the client) propagates into the endpoint call.
        withContext(context.minusKey(Job)) {
            if (endpoint.isEmpty()) {
                close(channelId)
                env.serialization.createCallData(Unit.serializer(), Unit)
            } else {
                // Errors thrown out of channel.call are now produced by RpcMethod.call as
                // CallData.Error frames — this channel is oblivious to error encoding and
                // simply forwards whatever variant comes back. Only routing-level failures
                // (channel lookup miss, top-level dispatch issues) hit the catch below.
                channel.call(endpoint, data, callId)
            }
        }
    } catch (t: CancellationException) {
        throw t
    } catch (t: Throwable) {
        env.logger.info("SerializedChannel", "Exception thrown during dispatching", t)
        env.errorListener.onError(t)
        val code = when (t) {
            is RpcEndpointException -> KsrpcException.ENDPOINT_NOT_FOUND_CODE
            is KsrpcException -> t.code
            else -> KsrpcException.INTERNAL_ERROR_CODE
        }
        CallData.Error(code, t.asString)
    }

    override suspend fun close(id: ChannelId) {
        env.logger.debug("SerializedChannel", "Closing channel ${id.id}")
        serviceMap.remove(id.id)?.let {
            it.trackingService?.onSerializationClosed(it)
            it.close()
        }
    }

    override suspend fun close() {
        if (isClosed) return
        isClosed = true
        env.logger.debug("SerializedChannel", "Closing entire channel")
        serviceMap.values.forEach {
            it.trackingService?.onSerializationClosed(it)
            it.close()
        }
        serviceMap.clear()
        val observers = onCloseObservers.toList()
        onCloseObservers.clear()
        observers.forEach { it.invoke() }
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
        // Route in-process sub-service access through a SubserviceChannel just like the
        // asClient wrapper does. Returning the bare service from serviceMap here bypasses the
        // close-remove path, which then leaves the entry in serviceMap until the parent
        // channel closes — causing a double-close (explicit + cascade) on the sub-service.
        env.logger.debug("SerializedChannel", "Wrapping local channel ${channelId.id}")
        return SubserviceChannel(asClient, channelId)
    }

    private val SerializedService<T>.trackingService: TrackingService?
        get() = (this as? HostSerializedServiceImpl<*, T>)?.service as? TrackingService
}

@KsrpcInternal
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
    override val env: KsrpcEnvironment<S>,
    private val serviceName: String = rpcObject.serviceName
) : SerializedService<S> {
    private val onCloseCallbacks = mutableSetOf<suspend () -> Unit>()

    override suspend fun call(
        endpoint: String,
        input: CallData<S>,
        callId: RpcCallId?
    ): CallData<S> {
        val rpcEndpoint = try {
            rpcObject.findEndpoint(endpoint)
        } catch (t: RpcEndpointException) {
            // Opt-in fallback: if the service implements UnhandledMethodHandler, route
            // unknown endpoints to it instead of propagating the endpoint-not-found error.
            val handler = service as? UnhandledMethodHandler
            if (handler != null) {
                return handler.onUnhandled(endpoint, input)
            }
            throw t
        }
        return rpcEndpoint.call(this, service, input, callId)
    }

    override suspend fun onClose(onClose: suspend () -> Unit) {
        onCloseCallbacks.add(onClose)
    }

    override suspend fun close() {
        (service as? SuspendCloseable)?.close()
        onCloseCallbacks.forEach { it.invoke() }
    }
}
