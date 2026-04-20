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
import com.monkopedia.ksrpc.RpcMethod
import com.monkopedia.ksrpc.RpcObject
import com.monkopedia.ksrpc.RpcService
import com.monkopedia.ksrpc.ServiceExecutor
import com.monkopedia.ksrpc.SubserviceTransformer
import com.monkopedia.ksrpc.channels.SerializedService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.serialization.KSerializer

/**
 * [RpcObject] for [KsFlowService] parameterized by item type.
 *
 * A new instance is needed for each concrete `T` because the sub-service
 * transformers for [KsFlowCollector] carry a serializer for `T`.
 */
class KsFlowServiceObject<T>(
    private val itemSerializer: KSerializer<T>
) : RpcObject<KsFlowService<T>> {
    internal val collectorObject = KsFlowCollectorObject(itemSerializer)

    override val serviceName: String = "KsFlowService"
    override val endpoints: List<String> = listOf("/collect")

    private val collectMethod: RpcMethod<*, *, *> by lazy {
        RpcMethod<KsFlowService<T>, KsFlowCollector<T>, KsCollectionToken>(
            endpoint = "/collect",
            inputTransform = SubserviceTransformer(collectorObject),
            outputTransform = SubserviceTransformer(KsCollectionTokenObject),
            method = object : ServiceExecutor {
                @Suppress("UNCHECKED_CAST")
                override suspend fun invoke(service: RpcService, input: Any?): Any? {
                    return (service as KsFlowService<T>)
                        .startCollection(input as KsFlowCollector<T>)
                }
            },
            metadata = emptyList()
        )
    }

    override fun <S> createStub(channel: SerializedService<S>): KsFlowService<T> =
        KsFlowServiceStub(channel, this)

    override fun findEndpoint(endpoint: String): RpcMethod<*, *, *> = when (endpoint) {
        "/collect" -> collectMethod
        else -> throw RpcEndpointException("Unknown endpoint: $endpoint")
    }
}

/**
 * Client-side stub for [KsFlowService].
 *
 * The [collect] implementation (from [kotlinx.coroutines.flow.Flow]):
 * 1. Creates a [KsFlowCollector] that feeds items into the standard [FlowCollector]
 * 2. Calls [startCollection] to get a [KsCollectionToken]
 * 3. Suspends until [onComplete] or [onError]
 * 4. On coroutine cancellation, calls [KsCollectionToken.cancelCollection]
 */
private class KsFlowServiceStub<T, S>(
    private val channel: SerializedService<S>,
    private val obj: KsFlowServiceObject<T>
) : KsFlowService<T> {

    override suspend fun startCollection(collector: KsFlowCollector<T>): KsCollectionToken {
        @Suppress("UNCHECKED_CAST")
        return obj.findEndpoint("/collect").callChannel(channel, collector) as KsCollectionToken
    }

    override suspend fun collect(collector: FlowCollector<T>) {
        val completion = CompletableDeferred<Unit>()
        val collectorImpl = object : KsFlowCollector<T> {
            override suspend fun onItem(item: T) {
                collector.emit(item)
            }

            override suspend fun onError(error: com.monkopedia.ksrpc.RpcFailure) {
                completion.completeExceptionally(error.toException())
            }

            override suspend fun onComplete(u: Unit) {
                completion.complete(Unit)
            }
        }
        val token = startCollection(collectorImpl)
        try {
            completion.await()
        } catch (e: kotlinx.coroutines.CancellationException) {
            token.cancelCollection(Unit)
            throw e
        }
    }

    override suspend fun close() {
        channel.close()
    }
}
