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
package com.monkopedia.ksrpc.channels

import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.RpcMethod
import com.monkopedia.ksrpc.RpcObject
import com.monkopedia.ksrpc.RpcService
import com.monkopedia.ksrpc.SuspendCloseableObservable
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.channels.ChannelClient.Companion.DEFAULT
import com.monkopedia.ksrpc.internal.HostSerializedServiceImpl
import com.monkopedia.ksrpc.rpcObject
import io.ktor.utils.io.ByteReadChannel
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Register a service to be hosted, the [ChannelId] ollocated to this service
 * is returned. Generally this should not be called directly, as it will happen
 * automatically when services are returned from [KsMethod] tagged methods.
 */
suspend inline fun <reified T : RpcService> ChannelHost.registerHost(
    service: T
): ChannelId = registerHost(service, rpcObject())

/**
 * Register a service to be hosted on the default channel.
 */
suspend inline fun <reified T : RpcService> SingleChannelHost.registerDefault(
    service: T
) = registerDefault(service, rpcObject())

/**
 * Register a service to be hosted, the [ChannelId] ollocated to this service
 * is returned. Generally this should not be called directly, as it will happen
 * automatically when services are returned from [KsMethod] tagged methods.
 */
suspend fun <T : RpcService> ChannelHost.registerHost(
    service: T,
    obj: RpcObject<T>
): ChannelId {
    return registerHost(HostSerializedServiceImpl(service, obj, env))
}

/**
 * Register a service to be hosted on the default channel.
 */
suspend fun <T : RpcService> SingleChannelHost.registerDefault(
    service: T,
    obj: RpcObject<T>
) {
    registerDefault(HostSerializedServiceImpl(service, obj, env))
}

internal interface ChannelHostProvider {
    val host: ChannelHost?
}

/**
 * A wrapper around a communication pathway that can be turned into a primary
 * SerializedService.
 */
interface SingleChannelHost : KsrpcEnvironment.Element {
    /**
     * Register the primary service to be hosted on this communication channel.
     *
     * The coroutine context and dispatcher on which calls are executed in on depends
     * on the construction of the host.
     */
    suspend fun registerDefault(service: SerializedService)
}

/**
 * A [SerializedChannel] that can host sub-services.
 *
 * This could be a bidirectional conduit like a [Connection], or it could be a hosting only
 * service such as http hosting.
 */
interface ChannelHost : SerializedChannel, SingleChannelHost, KsrpcEnvironment.Element {
    /**
     * Add a serialized service that can receive calls on this channel with the returned
     * [ChannelId]. The calls will be allowed until [close] is called.
     *
     * Generally this shouldn't need to be called directly, as services returned from
     * [KsMethod]s are automatically registered and translated across a channel.
     */
    suspend fun registerHost(service: SerializedService): ChannelId
}

internal interface ChannelHostInternal : ChannelHost, ChannelHostProvider {
    override val host: ChannelHost
        get() = this
}

internal interface ChannelClientProvider {
    val client: ChannelClient?
}

/**
 * A wrapper around a communication pathway that can be turned into a primary
 * SerializedService.
 */
interface SingleChannelClient {

    /**
     * Get a [SerializedService] that is the default on this client
     */
    suspend fun defaultChannel(): SerializedService
}

/**
 * A [SerializedChannel] that can call into sub-services.
 *
 * This could be a bidirectional conduit like a [Connection], or it could be a client only
 * service such as http client.
 */
interface ChannelClient : SerializedChannel, SingleChannelClient, KsrpcEnvironment.Element {
    /**
     * Takes a given channel id and creates a service wrapper to make calls on that channel.
     *
     * Generally this shouldn't be called directly, as services returned from [KsMethod]s
     * will automatically be wrapped before being returned from stubs.
     */
    suspend fun wrapChannel(channelId: ChannelId): SerializedService

    /**
     * Get a [SerializedService] that is the default on this client
     * (i.e. using [DEFAULT] channel id). This should act as the root service for most scenarios.
     */
    override suspend fun defaultChannel() = wrapChannel(ChannelId(DEFAULT))

    companion object {
        /**
         * Default Channel ID used for communication, any other channels should be created using
         * calls to the default channel id.
         */
        const val DEFAULT = ""
    }
}

internal interface ChannelClientInternal : ChannelClient, ChannelClientProvider {
    override val client: ChannelClient
        get() = this
}

/**
 * Generic interface for things that hold a context that is used when interacting with them.
 */
interface ContextContainer {
    val context: CoroutineContext
        get() = EmptyCoroutineContext
}

/**
 * A multi-channel interface for serialized communication, generally shouldn't need to interact
 * directly with this, instead use either [ChannelClient] or [ChannelHost] to reference a
 * [SerializedService] instead.
 */
interface SerializedChannel :
    SuspendCloseableObservable,
    ContextContainer,
    KsrpcEnvironment.Element {
    suspend fun call(channelId: ChannelId, endpoint: String, data: CallData): CallData
    suspend fun close(id: ChannelId)
}

/**
 * Serialized version of a service. This can be transformed to and from a service using
 * [serialized] and [SerializedService.toStub].
 */
interface SerializedService :
    SuspendCloseableObservable,
    ContextContainer,
    KsrpcEnvironment.Element {
    suspend fun call(endpoint: String, input: CallData): CallData
    suspend fun call(endpoint: RpcMethod<*, *, *>, input: CallData): CallData =
        call(endpoint.endpoint, input)
}

internal expect fun randomUuid(): String

/**
 * Wrapper around data being serialized through calls.
 * Could be a reference to a string for a serialized object or to binary data.
 */
data class CallData private constructor(private val value: Any?) {
    val isBinary: Boolean
        get() = value is ByteReadChannel

    /**
     * Read the serialized content of this object.
     * If this is not a string then throws []IllegalStateException].
     */
    fun readSerialized(): String {
        if (isBinary) error("Cannot read serialization out of binary data.")
        return value as String
    }

    /**
     * Get the [ByteReadChannel] for the binary data held by this call..
     * If this is not binary data then throws []IllegalStateException].
     */
    fun readBinary(): ByteReadChannel {
        if (!isBinary) error("Cannot read binary data out of serialized content.")
        return value as ByteReadChannel
    }

    override fun toString(): String {
        if (isBinary) {
            return "binary(${(value as? ByteReadChannel)?.availableForRead})"
        }
        return value.toString()
    }

    companion object {
        /**
         * Create a CallData holding content serialized in a string.
         */
        fun create(str: String) = CallData(str)

        /**
         * Create a CallData wrapping a [ByteReadChannel] for reading binary data.
         */
        fun create(binary: ByteReadChannel) = CallData(binary)
    }
}
