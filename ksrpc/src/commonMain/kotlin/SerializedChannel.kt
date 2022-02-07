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

import com.monkopedia.ksrpc.internal.HostSerializedServiceImpl
import io.ktor.utils.io.ByteReadChannel
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.serialization.StringFormat

suspend inline fun <reified T : RpcService> ChannelHost.registerHost(
    service: T
): ChannelId = registerHost(service, rpcObject())

suspend inline fun <reified T : RpcService> ChannelHost.registerDefault(
    service: T
) = registerDefault(service, rpcObject())

suspend fun <T : RpcService> ChannelHost.registerHost(
    service: T,
    obj: RpcObject<T>
): ChannelId {
    return registerHost(HostSerializedServiceImpl(service, obj, env))
}

suspend fun <T : RpcService> ChannelHost.registerDefault(
    service: T,
    obj: RpcObject<T>
) {
    registerDefault(HostSerializedServiceImpl(service, obj, env))
}

interface ChannelHostProvider {
    val host: ChannelHost?
}

interface ChannelHost : SerializedChannel, ChannelHostProvider, KsrpcElement {
    suspend fun registerHost(service: SerializedService): ChannelId
    suspend fun registerDefault(service: SerializedService)

    override val host: ChannelHost
        get() = this
}

interface ChannelClientProvider {
    val client: ChannelClient?
}

interface ConnectionProvider : ChannelHostProvider, ChannelClientProvider

interface ChannelClient : SerializedChannel, ChannelClientProvider, KsrpcElement {
    suspend fun wrapChannel(channelId: ChannelId): SerializedService
    suspend fun defaultChannel() = wrapChannel(ChannelId(DEFAULT))

    override val client: ChannelClient
        get() = this

    companion object {
        const val DEFAULT = ""
    }
}

interface Serializing {
    val serialization: StringFormat
}

interface ContextContainer {
    val context: CoroutineContext
        get() = EmptyCoroutineContext
}

interface SerializedChannel : SuspendCloseableObservable, ContextContainer, KsrpcElement {
    suspend fun call(channelId: ChannelId, endpoint: String, data: CallData): CallData
    suspend fun close(id: ChannelId)
}

interface SerializedService : SuspendCloseableObservable, ContextContainer, KsrpcElement {
    suspend fun call(endpoint: String, input: CallData): CallData
}

expect fun randomUuid(): String

fun <T : RpcService> T.serialized(
    rpcObject: RpcObject<T>,
    env: KsrpcEnvironment
): SerializedService {
    val rpcChannel = this
    return HostSerializedServiceImpl(rpcChannel, rpcObject, env)
}

data class CallData private constructor(private val value: Any?) {
    val isBinary: Boolean
        get() = value is ByteReadChannel

    fun readSerialized(): String {
        if (isBinary) error("Cannot read serialization out of binary data.")
        return value as String
    }

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
        fun create(str: String) = CallData(str)
        fun create(binary: ByteReadChannel) = CallData(binary)
    }
}
