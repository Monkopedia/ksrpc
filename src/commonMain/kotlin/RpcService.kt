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

import io.ktor.utils.io.ByteReadChannel
import kotlin.reflect.KClass
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

interface RpcService : SuspendCloseable {
    suspend fun <I, O> map(
        endpoint: String,
        inputSer: KSerializer<I>,
        outputSer: KSerializer<O>,
        input: I,
    ): O

    suspend fun <O> mapBinaryInput(
        endpoint: String,
        outputSer: KSerializer<O>,
        input: ByteReadChannel,
    ): O

    suspend fun <I> mapBinary(
        endpoint: String,
        inputSer: KSerializer<I>,
        input: I,
    ): ByteReadChannel

    suspend fun <I, O : RpcService> service(
        endpoint: String,
        service: RpcObject<O>,
        inputSer: KSerializer<I>,
        input: I,
    ): O
}

open class Service : RpcService {
    final override suspend fun <I, O> map(
        endpoint: String,
        inputSer: KSerializer<I>,
        outputSer: KSerializer<O>,
        input: I,
    ): O {
        throw NotImplementedError("Service did not implement $endpoint")
    }

    override suspend fun <O> mapBinaryInput(
        endpoint: String,
        outputSer: KSerializer<O>,
        input: ByteReadChannel,
    ): O {
        throw NotImplementedError("Service did not implement $endpoint")
    }

    override suspend fun <I> mapBinary(
        endpoint: String,
        inputSer: KSerializer<I>,
        input: I,
    ): ByteReadChannel {
        throw NotImplementedError("Service did not implement $endpoint")
    }

    final override suspend fun <I, O : RpcService> service(
        endpoint: String,
        service: RpcObject<O>,
        inputSer: KSerializer<I>,
        input: I,
    ): O {
        throw NotImplementedError("Service did not implement $endpoint")
    }

    override suspend fun close() {
        throw IllegalStateException("Service did not expect close")
    }
}

suspend inline fun <reified I, reified O> RpcService.map(
    endpoint: String,
    input: I,
): O {
    return map(endpoint, serializer(), serializer(), input)
}

suspend inline fun <reified I> RpcService.mapBinary(
    endpoint: String,
    input: I,
): ByteReadChannel {
    return mapBinary(endpoint, serializer(), input)
}

suspend inline fun <reified O> RpcService.mapBinaryInput(
    endpoint: String,
    input: ByteReadChannel,
): O {
    return mapBinaryInput(endpoint, serializer(), input)
}

suspend inline fun <reified I, reified O : RpcService> RpcService.service(
    endpoint: String,
    service: RpcObject<O>,
    input: I,
): O {
    return service(endpoint, service, serializer(), input)
}

class RpcServiceChannel internal constructor(private val channel: RpcChannel) : RpcService {
    override suspend fun <I, O> map(
        endpoint: String,
        inputSer: KSerializer<I>,
        outputSer: KSerializer<O>,
        input: I,
    ): O = channel.call(endpoint.trim('/'), inputSer, outputSer, input)

    override suspend fun <I> mapBinary(
        endpoint: String,
        inputSer: KSerializer<I>,
        input: I,
    ): ByteReadChannel = channel.callBinary(endpoint.trim('/'), inputSer, input)

    override suspend fun <O> mapBinaryInput(
        endpoint: String,
        outputSer: KSerializer<O>,
        input: ByteReadChannel,
    ): O = channel.callBinaryInput(endpoint.trim('/'), outputSer, input)

    override suspend fun <I, O : RpcService> service(
        endpoint: String,
        service: RpcObject<O>,
        inputSer: KSerializer<I>,
        input: I,
    ): O = channel.callService(endpoint.trim('/'), service, inputSer, input)

    override suspend fun close() = channel.close()
}

open class RpcObject<T : RpcService>(cls: KClass<T>, stubFactory: (RpcServiceChannel) -> T) {
    internal val info = RpcServiceInfo(cls, stubFactory)

    fun wrap(channel: RpcChannel): T {
        return info.createStubFor(channel)
    }

    fun wrap(channel: SerializedChannel): T {
        return info.createStubFor(channel.deserialized())
    }

    fun channel(service: T): RpcChannel {
        return info.createChannelFor(service)
    }
}
