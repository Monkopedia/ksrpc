/*
 * Copyright (C) 2026 Jason Monk <monkopedia@gmail.com>
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
package com.monkopedia.ksrpc.flow

import com.monkopedia.ksrpc.RpcEndpointException
import com.monkopedia.ksrpc.RpcFailure
import com.monkopedia.ksrpc.RpcMethod
import com.monkopedia.ksrpc.RpcObject
import com.monkopedia.ksrpc.RpcService
import com.monkopedia.ksrpc.SerializerTransformer
import com.monkopedia.ksrpc.ServiceExecutor
import com.monkopedia.ksrpc.channels.SerializedService
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer

/**
 * [RpcObject] for [KsFlowCollector] parameterized by item type.
 *
 * A new instance is needed for each concrete `T` because the `onItem`
 * endpoint carries a value of type `T`.
 */
class KsFlowCollectorObject<T>(
    private val itemSerializer: KSerializer<T>
) : RpcObject<KsFlowCollector<T>> {
    override val serviceName: String = "KsFlowCollector"
    override val endpoints: List<String> = listOf("/onItem", "/onError", "/onComplete")

    private val onItemMethod: RpcMethod<*, *, *> by lazy {
        RpcMethod<KsFlowCollector<T>, T, Unit>(
            endpoint = "/onItem",
            inputTransform = SerializerTransformer(itemSerializer),
            outputTransform = SerializerTransformer(Unit.serializer()),
            method = object : ServiceExecutor {
                @Suppress("UNCHECKED_CAST")
                override suspend fun invoke(service: RpcService, input: Any?): Any? {
                    (service as KsFlowCollector<T>).onItem(input as T)
                    return Unit
                }
            },
            metadata = emptyList()
        )
    }

    private val onErrorMethod: RpcMethod<*, *, *> by lazy {
        RpcMethod<KsFlowCollector<T>, RpcFailure, Unit>(
            endpoint = "/onError",
            inputTransform = SerializerTransformer(RpcFailure.serializer()),
            outputTransform = SerializerTransformer(Unit.serializer()),
            method = object : ServiceExecutor {
                override suspend fun invoke(service: RpcService, input: Any?): Any? {
                    (service as KsFlowCollector<*>).onError(input as RpcFailure)
                    return Unit
                }
            },
            metadata = emptyList()
        )
    }

    private val onCompleteMethod: RpcMethod<*, *, *> by lazy {
        RpcMethod<KsFlowCollector<T>, Unit, Unit>(
            endpoint = "/onComplete",
            inputTransform = SerializerTransformer(Unit.serializer()),
            outputTransform = SerializerTransformer(Unit.serializer()),
            method = object : ServiceExecutor {
                override suspend fun invoke(service: RpcService, input: Any?): Any? {
                    (service as KsFlowCollector<*>).onComplete(Unit)
                    return Unit
                }
            },
            metadata = emptyList()
        )
    }

    override fun <S> createStub(channel: SerializedService<S>): KsFlowCollector<T> =
        KsFlowCollectorStub(channel, this)

    override fun findEndpoint(endpoint: String): RpcMethod<*, *, *> = when (endpoint) {
        "/onItem" -> onItemMethod
        "/onError" -> onErrorMethod
        "/onComplete" -> onCompleteMethod
        else -> throw RpcEndpointException("Unknown endpoint: $endpoint")
    }
}

private class KsFlowCollectorStub<T, S>(
    private val channel: SerializedService<S>,
    private val obj: KsFlowCollectorObject<T>
) : KsFlowCollector<T> {
    override suspend fun onItem(item: T) {
        obj.findEndpoint("/onItem").callChannel(channel, item)
    }

    override suspend fun onError(error: RpcFailure) {
        obj.findEndpoint("/onError").callChannel(channel, error)
    }

    override suspend fun onComplete(u: Unit) {
        obj.findEndpoint("/onComplete").callChannel(channel, u)
    }

    override suspend fun close() {
        channel.close()
    }
}
