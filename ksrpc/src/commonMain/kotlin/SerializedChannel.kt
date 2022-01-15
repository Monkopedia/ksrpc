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
import kotlinx.serialization.StringFormat
import kotlinx.serialization.json.Json

fun <T : RpcService> ChannelHost.registerHost(
    service: T,
    obj: RpcObject<T>,
    serialization: StringFormat = this.serialization
): ChannelId {
    return registerHost(HostSerializedServiceImpl(service, obj, serialization))
}

fun <T : RpcService> ChannelHost.registerDefault(
    service: T,
    obj: RpcObject<T>,
    serialization: StringFormat = this.serialization
) {
    registerDefault(HostSerializedServiceImpl(service, obj, serialization))
}

interface ChannelHostProvider {
    val host: ChannelHost?
}

interface ChannelHost : Serializing, ChannelHostProvider {
    fun registerHost(service: SerializedService): ChannelId
    fun registerDefault(service: SerializedService)
    suspend fun close(id: ChannelId)

    override val host: ChannelHost
        get() = this
}

interface ChannelClientProvider {
    val client: ChannelClient?
}

interface ChannelClient : SerializedChannel, ChannelClientProvider {
    fun wrapChannel(channelId: ChannelId): SerializedService
    fun defaultChannel() = wrapChannel(ChannelId(DEFAULT))

    override val client: ChannelClient
        get() = this

    companion object {
        const val DEFAULT = ""
    }
}

interface Serializing {
    val serialization: StringFormat
}

interface SerializedChannel : SuspendCloseable, Serializing {
    suspend fun call(channelId: ChannelId, endpoint: String, data: CallData): CallData
    suspend fun close(id: ChannelId)
}

interface SerializedService : SuspendCloseable, Serializing {
    suspend fun call(endpoint: String, input: CallData): CallData
}

expect fun randomUuid(): String

fun <T : RpcService> T.serialized(
    rpcObject: RpcObject<T>,
    errorListener: ErrorListener = ErrorListener { },
    json: Json = Json { isLenient = true }
): SerializedService {
    val rpcChannel = this
    return HostSerializedServiceImpl(rpcChannel, rpcObject, json)
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

fun <T : RpcService> RpcObject<T>.serializedChannel(
    service: T,
    errorListener: ErrorListener = ErrorListener { }
): SerializedService {
    return service.serialized(this, errorListener = errorListener)
}
