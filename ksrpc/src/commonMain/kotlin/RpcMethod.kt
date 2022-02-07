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

import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.StringFormat
import kotlinx.serialization.builtins.serializer
import kotlin.coroutines.coroutineContext

internal sealed interface Transformer<T> {
    suspend fun transform(input: T, channel: SerializedService): CallData
    suspend fun untransform(data: CallData, channel: SerializedService): T

    fun unpackError(data: CallData, serialization: StringFormat) {
        if (!data.isBinary) {
            val outputStr = data.readSerialized()
            if (outputStr.startsWith(ERROR_PREFIX)) {
                val errorStr = outputStr.substring(ERROR_PREFIX.length)
                println("Unpacking error:\n$errorStr")
                println("Length: ${errorStr.length} Ending: ${errorStr.substring(errorStr.length - 5)}")
                throw serialization.decodeFromString(RpcFailure.serializer(), errorStr)
                    .toException()
            }
        }
    }
}

internal class SerializerTransformer<I>(private val serializer: KSerializer<I>) : Transformer<I> {
    override suspend fun transform(input: I, channel: SerializedService): CallData {
        return CallData.create(channel.env.serialization.encodeToString(serializer, input))
    }

    override suspend fun untransform(data: CallData, channel: SerializedService): I {
        unpackError(data, channel.env.serialization)
        return channel.env.serialization.decodeFromString(serializer, data.readSerialized())
    }
}

internal object BinaryTransformer : Transformer<ByteReadChannel> {
    override suspend fun transform(input: ByteReadChannel, channel: SerializedService): CallData {
        return CallData.create(input)
    }

    override suspend fun untransform(data: CallData, channel: SerializedService): ByteReadChannel {
        unpackError(data, channel.env.serialization)
        return data.readBinary()
    }
}

internal class SubserviceTransformer<T : RpcService>(
    private val serviceObj: RpcObject<T>
) : Transformer<T> {
    override suspend fun transform(input: T, channel: SerializedService): CallData {
        val host = host() ?: error("Cannot transform service type to non-hosting channel ${coroutineContext[HostChannelContext.Key]?.channel}")
        val serviceId = host.registerHost(input, serviceObj)
        return CallData.create(
            channel.env.serialization.encodeToString(String.serializer(), serviceId.id)
        )
    }

    override suspend fun untransform(data: CallData, channel: SerializedService): T {
        val client = client() ?: error("Cannot untransform service type from non-client channel ${coroutineContext[ClientChannelContext.Key]?.channel}")
        unpackError(data, channel.env.serialization)
        val serviceId = channel.env.serialization.decodeFromString(
            String.serializer(),
            data.readSerialized()
        )
        return serviceObj.createStub(client.wrapChannel(ChannelId(serviceId)))
    }
}

internal interface ServiceExecutor {
    suspend fun invoke(service: RpcService, input: Any?): Any?
}

class RpcMethod<T : RpcService, I, O> internal constructor(
    private val endpoint: String,
    private val inputTransform: Transformer<I>,
    private val outputTransform: Transformer<O>,
    private val method: ServiceExecutor
) {
    internal suspend fun call(
        channel: SerializedService,
        service: RpcService,
        input: CallData
    ): CallData {
        return withContext(channel.context) {
            val transformedInput = inputTransform.untransform(input, channel)
            val output = method.invoke(service as T, transformedInput)
            outputTransform.transform(output as O, channel)
        }
    }

    internal suspend fun callChannel(channel: SerializedService, input: Any?): Any? {
        return withContext(channel.context) {
            val input = inputTransform.transform(input as I, channel)
            val transformedOutput = channel.call(endpoint, input)
            outputTransform.untransform(transformedOutput, channel)
        }
    }
}
