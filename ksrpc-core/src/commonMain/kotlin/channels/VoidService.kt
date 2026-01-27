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
package com.monkopedia.ksrpc.channels

import com.monkopedia.ksrpc.IntrospectionService
import com.monkopedia.ksrpc.IntrospectionServiceImpl
import com.monkopedia.ksrpc.RpcEndpointException
import com.monkopedia.ksrpc.RpcMethod
import com.monkopedia.ksrpc.RpcObject
import com.monkopedia.ksrpc.RpcObjectKey
import com.monkopedia.ksrpc.RpcService
import com.monkopedia.ksrpc.SerializerTransformer
import com.monkopedia.ksrpc.ServiceExecutor
import com.monkopedia.ksrpc.SubserviceTransformer
import com.monkopedia.ksrpc.rpcObject
import kotlinx.serialization.builtins.serializer

@RpcObjectKey(VoidService.Companion::class)
internal interface VoidService : RpcService {
    companion object : RpcObject<VoidService> {
        private val introspection = RpcMethod<VoidService, Unit, IntrospectionService>(
            "introspection",
            SerializerTransformer(Unit.serializer()),
            SubserviceTransformer(rpcObject<IntrospectionService>()),
            object : ServiceExecutor {
                override suspend fun invoke(service: RpcService, input: Any?): Any? =
                    IntrospectionServiceImpl(Companion)
            }
        )
        override val serviceName: String = "com.monkopedia.ksrpc.channels.VoidService"
        override val endpoints: List<String> = listOf(introspection.endpoint)

        override fun <T> createStub(channel: SerializedService<T>): VoidService =
            object : VoidService {

                override suspend fun getIntrospection(u: Unit): IntrospectionService =
                    introspection.callChannel(channel, u) as IntrospectionService
            }

        override fun findEndpoint(endpoint: String): RpcMethod<*, *, *> =
            if (endpoint == introspection.endpoint) {
                introspection
            } else {
                throw RpcEndpointException("VoidService has no endpoints")
            }
    }
}
