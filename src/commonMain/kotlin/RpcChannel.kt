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

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

fun interface ErrorListener {
    fun onError(t: Throwable)
}

interface RpcChannel {

    suspend fun <I, O> call(
        endpoint: String,
        inputSer: KSerializer<I>,
        outputSer: KSerializer<O>,
        input: I
    ): O

    suspend fun <I, O : RpcService> callService(
        endpoint: String,
        service: RpcObject<O>,
        inputSer: KSerializer<I>,
        input: I
    ): O

    suspend fun close()
}

interface SerializedChannel {
    suspend fun call(str: String, input: String): String

    suspend fun close()
}

expect val Throwable.asString: String

internal const val ERROR_PREFIX = "ERROR:"

fun SerializedChannel.deserialized(json: Json = Json { isLenient = true }): RpcChannel {
    return RpcChannelImpl(json, this)
}

private class RpcChannelImpl(
    private val json: Json,
    private val serializedChannel: SerializedChannel
) : RpcChannel {
    override suspend fun <I, O> call(
        endpoint: String,
        inputSer: KSerializer<I>,
        outputSer: KSerializer<O>,
        input: I
    ): O {
        val inputStr = input?.let { json.encodeToString(inputSer, input) } ?: ""
        val outputStr = serializedChannel.call(endpoint, inputStr)
        if (outputStr.startsWith(ERROR_PREFIX)) {
            val errorStr = outputStr.substring(ERROR_PREFIX.length)
            throw json.decodeFromString(RpcFailure.serializer(), errorStr).toException()
        }
        return json.decodeFromString(outputSer, outputStr)
    }

    override suspend fun <I, O : RpcService> callService(
        endpoint: String,
        service: RpcObject<O>,
        inputSer: KSerializer<I>,
        input: I
    ): O {
        val serviceId = call(endpoint, inputSer, String.serializer(), input)
        return service.wrap(SubserviceChannel(this, serviceId))
    }

    override suspend fun close() {
        serializedChannel.close()
    }
}

private class SubserviceChannel(
    private val baseChannel: RpcChannel,
    private val serviceId: String
) : RpcChannel {
    override suspend fun <I, O> call(
        endpoint: String,
        inputSer: KSerializer<I>,
        outputSer: KSerializer<O>,
        input: I
    ): O {
        val wrappedInput = ServiceCall(input, endpoint)
        val wrappedSerializer = ServiceCall.serializer(inputSer)
        return baseChannel.call(serviceId, wrappedSerializer, outputSer, wrappedInput)
    }

    override suspend fun <I, O : RpcService> callService(
        endpoint: String,
        service: RpcObject<O>,
        inputSer: KSerializer<I>,
        input: I
    ): O {
        val serviceId = call(endpoint, inputSer, String.serializer(), input)
        return service.wrap(SubserviceChannel(this, serviceId))
    }

    override suspend fun close() {
        val wrappedInput = ServiceCall("", "close", true)
        val wrappedSerializer = ServiceCall.serializer(String.serializer())
        return baseChannel.call(serviceId, wrappedSerializer, Unit.serializer(), wrappedInput)
    }
}

@Serializable
data class ServiceCall<I>(
    val input: I,
    val endpoint: String,
    val close: Boolean = false
)

@Serializable
data class RpcFailure(val stack: String) {
    fun toException(): RuntimeException {
        return RpcException(stack)
    }
}

class RpcException(override val message: String) : RuntimeException(message)
