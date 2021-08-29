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

import com.monkopedia.ksrpc.internal.SerializedChannelImpl
import io.ktor.utils.io.ByteReadChannel
import kotlinx.serialization.StringFormat
import kotlinx.serialization.json.Json

interface SerializedChannel : SuspendCloseable {
    val serialization: StringFormat
    suspend fun call(endpoint: String, input: CallData): CallData
}

interface HostingSerializedChannel : SerializedChannel {
    fun <T : RpcService> registerSubService(serviceId: String, service: T, obj: RpcObject<T>)
}

expect fun randomUuid(): String

fun <T : RpcService> T.serialized(
    rpcObject: RpcObject<T>,
    errorListener: ErrorListener = ErrorListener { },
    json: Json = Json { isLenient = true }
): SerializedChannel {
    val rpcChannel = this
    return SerializedChannelImpl(rpcChannel, rpcObject, errorListener, json)
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
): SerializedChannel {
    return service.serialized(this, errorListener = errorListener)
}
