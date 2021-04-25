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

import io.ktor.utils.io.*
import kotlinx.serialization.*
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

    suspend fun <I> callBinary(endpoint: String, inputSer: KSerializer<I>, input: I): ByteReadChannel
    suspend fun <O> callBinaryInput(endpoint: String, outputSer: KSerializer<O>, input: ByteReadChannel): O

    suspend fun close()
}

interface SerializedChannel {
    suspend fun call(endpoint: String, input: String): String
    suspend fun callBinary(endpoint: String, input: String): ByteReadChannel
    suspend fun callBinaryInput(endpoint: String, input: ByteReadChannel): String

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

    override suspend fun <I> callBinary(trim: String, inputSer: KSerializer<I>, input: I): ByteReadChannel {
        val inputStr = input?.let { json.encodeToString(inputSer, input) } ?: ""
        return serializedChannel.callBinary(trim, inputStr)
    }

    override suspend fun <O> callBinaryInput(trim: String, outputSer: KSerializer<O>, input: ByteReadChannel): O {
        val outputStr = serializedChannel.callBinaryInput(trim, input)
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
        return service.wrap(SubserviceChannel(json, this, serviceId))
    }

    override suspend fun close() {
        serializedChannel.close()
    }
}

private class SubserviceChannel(
        private val json: Json,
    private val baseChannel: RpcChannel,
    private val serviceId: String
) : RpcChannel {
    override suspend fun <I, O> call(
        endpoint: String,
        inputSer: KSerializer<I>,
        outputSer: KSerializer<O>,
        input: I
    ): O {
        return call(listOf(endpoint), inputSer, outputSer, input)
    }

    private suspend fun <I, O> call(
            target: List<String>,
            inputSer: KSerializer<I>,
            outputSer: KSerializer<O>,
            input: I
    ): O {
        if (baseChannel is SubserviceChannel) {
            return baseChannel.call(target + serviceId, inputSer, outputSer, input)
        }
        return baseChannel.call(json.encodedEndpoint(target), inputSer, outputSer, input)
    }

    override suspend fun <I> callBinary(endpoint: String, inputSer: KSerializer<I>, input: I): ByteReadChannel {
        return callBinary(listOf(endpoint), inputSer, input)
    }

    private suspend fun <I> callBinary(
            target: List<String>,
            inputSer: KSerializer<I>,
            input: I
    ): ByteReadChannel {
        if (baseChannel is SubserviceChannel) {
            return baseChannel.callBinary(target + serviceId, inputSer, input)
        }
        return baseChannel.callBinary(json.encodedEndpoint(target), inputSer, input)
    }

    override suspend fun <O> callBinaryInput(endpoint: String, outputSer: KSerializer<O>, input: ByteReadChannel): O {
        return callBinaryInput(listOf(endpoint), outputSer, input)
    }

    private suspend fun <O> callBinaryInput(target: List<String>, outputSer: KSerializer<O>, input: ByteReadChannel): O {
        if (baseChannel is SubserviceChannel) {
            return baseChannel.callBinaryInput(target + serviceId, outputSer, input)
        }
        return baseChannel.callBinaryInput(json.encodedEndpoint(target), outputSer, input)
    }

    override suspend fun <I, O : RpcService> callService(
        endpoint: String,
        service: RpcObject<O>,
        inputSer: KSerializer<I>,
        input: I
    ): O {
        val serviceId = call(endpoint, inputSer, String.serializer(), input)
        return service.wrap(SubserviceChannel(json, this, serviceId))
    }

    override suspend fun close() {
//        val wrappedInput = ServiceCall("", "close", true)
//        val wrappedSerializer = ServiceCall.serializer(String.serializer())
        return baseChannel.call(json.encodedEndpoint(listOf("", "close")), Unit.serializer(), Unit.serializer(), Unit)
    }
}

private val SPLIT_CHAR = ":"

fun StringFormat.encodedEndpoint(endpoint: List<String>): String {
    return "${endpoint.first()}$SPLIT_CHAR${encodeToString(endpoint.subList(1, endpoint.size))}"
}

fun StringFormat.decodedEndpoint(endpoint: String): Pair<String, List<String>?> {
    val index = endpoint.indexOf(SPLIT_CHAR)
    if (index < 0) {
        return endpoint to null
    }
    return endpoint.substring(0, index) to decodeFromString(endpoint.substring(index + 1))
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
