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
import kotlinx.serialization.KSerializer
import kotlinx.serialization.StringFormat
import kotlinx.serialization.builtins.serializer

internal sealed interface Transformer<T> {
    fun transform(input: T, channel: SerializedChannel): CallData
    fun untransform(data: CallData, channel: SerializedChannel): T

    fun unpackError(data: CallData, serialization: StringFormat) {
        if (!data.isBinary) {
            val outputStr = data.readSerialized()
            if (outputStr.startsWith(ERROR_PREFIX)) {
                val errorStr = outputStr.substring(ERROR_PREFIX.length)
                throw serialization.decodeFromString(RpcFailure.serializer(), errorStr)
                    .toException()
            }
        }
    }
}

internal class SerializerTransformer<I>(private val serializer: KSerializer<I>) : Transformer<I> {
    override fun transform(input: I, channel: SerializedChannel): CallData {
        return CallData.create(channel.serialization.encodeToString(serializer, input))
    }

    override fun untransform(data: CallData, channel: SerializedChannel): I {
        unpackError(data, channel.serialization)
        return channel.serialization.decodeFromString(serializer, data.readSerialized())
    }
}

internal object BinaryTransformer : Transformer<ByteReadChannel> {
    override fun transform(input: ByteReadChannel, channel: SerializedChannel): CallData {
        return CallData.create(input)
    }

    override fun untransform(data: CallData, channel: SerializedChannel): ByteReadChannel {
        unpackError(data, channel.serialization)
        return data.readBinary()
    }
}

internal class SubserviceTransformer<T : RpcService>(
    private val serviceObj: RpcObject<T>
) : Transformer<T> {
    override fun transform(input: T, channel: SerializedChannel): CallData {
        val serviceId = randomUuid()
        (channel as? HostingSerializedChannel)?.registerSubService(serviceId, input, serviceObj)
        return CallData.create(
            channel.serialization.encodeToString(String.serializer(), serviceId)
        )
    }

    override fun untransform(data: CallData, channel: SerializedChannel): T {
        BinaryTransformer.unpackError(data, channel.serialization)
        val serviceId = channel.serialization.decodeFromString(
            String.serializer(),
            data.readSerialized()
        )
        return serviceObj.createStub(channel.subservice(serviceId))
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
        channel: SerializedChannel,
        service: RpcService,
        input: CallData
    ): CallData {
        val transformedInput = inputTransform.untransform(input, channel)
        val output = method.invoke(service as T, transformedInput)
        return outputTransform.transform(output as O, channel)
    }

    internal suspend fun callChannel(channel: SerializedChannel, input: Any?): Any? {
        val input = inputTransform.transform(input as I, channel)
        val transformedOutput = channel.call(endpoint, input)
        return outputTransform.untransform(transformedOutput, channel)
    }
}