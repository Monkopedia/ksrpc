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
import com.monkopedia.ksrpc.SerializerTransformer
import com.monkopedia.ksrpc.ServiceExecutor
import com.monkopedia.ksrpc.channels.SerializedService
import kotlinx.serialization.builtins.serializer

/**
 * [RpcObject] for [KsCollectionToken].
 *
 * Not generic -- [KsCollectionToken] has no type parameter.
 */
object KsCollectionTokenObject : RpcObject<KsCollectionToken> {
    override val serviceName: String = "KsCollectionToken"
    override val endpoints: List<String> = listOf("/cancel")

    private val cancelMethod: RpcMethod<*, *, *> by lazy {
        RpcMethod<KsCollectionToken, Unit, Unit>(
            endpoint = "/cancel",
            inputTransform = SerializerTransformer(Unit.serializer()),
            outputTransform = SerializerTransformer(Unit.serializer()),
            method = object : ServiceExecutor {
                override suspend fun invoke(service: RpcService, input: Any?): Any? {
                    (service as KsCollectionToken).cancelCollection(Unit)
                    return Unit
                }
            },
            metadata = emptyList()
        )
    }

    override fun <S> createStub(channel: SerializedService<S>): KsCollectionToken =
        KsCollectionTokenStub(channel)

    override fun findEndpoint(endpoint: String): RpcMethod<*, *, *> = when (endpoint) {
        "/cancel" -> cancelMethod
        else -> throw RpcEndpointException("Unknown endpoint: $endpoint")
    }
}

private class KsCollectionTokenStub<S>(
    private val channel: SerializedService<S>
) : KsCollectionToken {
    override suspend fun cancelCollection(u: Unit) {
        KsCollectionTokenObject.findEndpoint("/cancel").callChannel(channel, u)
    }

    override suspend fun close() {
        channel.close()
    }
}
