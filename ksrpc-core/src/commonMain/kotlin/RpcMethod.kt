/**
 * Copyright (C) 2025 Jason Monk <monkopedia@gmail.com>
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

import com.monkopedia.ksrpc.channels.CallData
import com.monkopedia.ksrpc.channels.ChannelId
import com.monkopedia.ksrpc.channels.SerializedService
import com.monkopedia.ksrpc.channels.randomUuid
import com.monkopedia.ksrpc.channels.registerHost
import com.monkopedia.ksrpc.internal.client
import com.monkopedia.ksrpc.internal.host
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer

internal sealed interface Transformer<T> {
    val hasContent: Boolean
        get() = true

    suspend fun <S> transform(input: T, channel: SerializedService<S>): CallData<S>
    suspend fun <S> untransform(data: CallData<S>, channel: SerializedService<S>): T

    fun <S> unpackError(data: CallData<S>, channel: SerializedService<S>) {
        if (!data.isBinary && channel.env.serialization.isError(data)) {
            throw channel.env.serialization.decodeErrorCallData(data).also {
                channel.env.logger.info("Transformer", "Decoding Throwable form CallData", it)
            }
        }
    }
}

internal class SerializerTransformer<I>(private val serializer: KSerializer<I>) : Transformer<I> {
    override val hasContent: Boolean
        get() = serializer != Unit.serializer()

    override suspend fun <T> transform(input: I, channel: SerializedService<T>): CallData<T> {
        channel.env.logger.debug("Transformer", "Serializing input to CallData")
        return channel.env.serialization.createCallData(serializer, input)
    }

    override suspend fun <T> untransform(data: CallData<T>, channel: SerializedService<T>): I {
        unpackError(data, channel)
        channel.env.logger.debug("Transformer", "Deserializing CallData to type")
        return channel.env.serialization.decodeCallData(serializer, data)
    }
}

internal object BinaryTransformer : Transformer<ByteReadChannel> {
    override suspend fun <T> transform(
        input: ByteReadChannel,
        channel: SerializedService<T>
    ): CallData<T> {
        channel.env.logger.debug("Transformer", "Serializing ByteReadChannel to CallData")
        return CallData.createBinary(input)
    }

    override suspend fun <T> untransform(
        data: CallData<T>,
        channel: SerializedService<T>
    ): ByteReadChannel {
        unpackError(data, channel)
        channel.env.logger.debug("Transformer", "Deserializing ByteReadChannel to CallData")
        return data.readBinary()
    }
}

internal class SubserviceTransformer<T : RpcService>(
    private val serviceObj: RpcObject<T>
) : Transformer<T> {
    override suspend fun <S> transform(input: T, channel: SerializedService<S>): CallData<S> {
        val host = host<S>() ?: error("Cannot transform service type to non-hosting channel")
        val serviceId = host.registerHost(input, serviceObj)
        channel.env.logger.info("Transformer", "Serializing Service to CallData(${serviceId.id})")
        return channel.env.serialization.createCallData(String.serializer(), serviceId.id)
    }

    override suspend fun <S> untransform(data: CallData<S>, channel: SerializedService<S>): T {
        val client = client<S>() ?: error("Cannot untransform service type from non-client channel")
        unpackError(data, channel)
        val serviceId = channel.env.serialization.decodeCallData(String.serializer(), data)
        channel.env.logger.info("Transformer", "Deserializing CallData($serviceId) to Stub")
        return serviceObj.createStub(client.wrapChannel(ChannelId(serviceId)))
    }
}

internal interface ServiceExecutor {
    suspend fun invoke(service: RpcService, input: Any?): Any?
}

/**
 * A wrapper around calling into or from stubs/serialization.
 */
class RpcMethod<T : RpcService, I, O> internal constructor(
    val endpoint: String,
    private val inputTransform: Transformer<I>,
    private val outputTransform: Transformer<O>,
    private val method: ServiceExecutor
) {

    val hasReturnType: Boolean
        get() = outputTransform.hasContent

    @Suppress("UNCHECKED_CAST")
    internal suspend fun <S> call(
        channel: SerializedService<S>,
        service: RpcService,
        input: CallData<S>
    ): CallData<S> {
        return withContext(channel.context) {
            val transformedInput = inputTransform.untransform(input, channel)
            val id = randomUuid()
            channel.env.logger.info("Transformer", "($id) Calling endpoint $endpoint")
            val output = method.invoke(service as T, transformedInput)
            channel.env.logger.debug("Transformer", "($id) Completed endpoint $endpoint")
            outputTransform.transform(output as O, channel)
        }
    }

    @Suppress("UNCHECKED_CAST")
    internal suspend fun <S> callChannel(channel: SerializedService<S>, input: Any?): Any? {
        return withContext(channel.context) {
            val input = inputTransform.transform(input as I, channel)
            val id = randomUuid()
            channel.env.logger.info("Transformer", "($id) Calling remote endpoint $endpoint")
            val transformedOutput = channel.call(this@RpcMethod, input)
            channel.env.logger.debug("Transformer", "($id) Completed remote endpoint $endpoint")
            outputTransform.untransform(transformedOutput, channel)
        }
    }
}
