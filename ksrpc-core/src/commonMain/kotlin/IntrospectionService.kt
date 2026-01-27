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
package com.monkopedia.ksrpc

import com.monkopedia.ksrpc.channels.SerializedService
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

internal const val SERVICE_NAME_ENDPOINT = "service_name"
internal const val ENDPOINTS_ENDPOINT = "endpoints"

@PublishedApi
internal class IntrospectionServiceImpl(private val rpcObject: RpcObject<*>) :
    IntrospectionService {
    override suspend fun getServiceName(u: Unit): String = rpcObject.serviceName

    override suspend fun getEndpoints(u: Unit): List<String> = rpcObject.endpoints

    override suspend fun getIntrospection(u: Unit): IntrospectionService =
        IntrospectionServiceImpl(IntrospectionServiceRpcObject)
}

@PublishedApi
internal object IntrospectionServiceRpcObject : RpcObject<IntrospectionService> {
    override val serviceName: String = "com.monkopedia.ksrpc.IntrospectionService"
    override val endpoints: List<String> = listOf(SERVICE_NAME_ENDPOINT, ENDPOINTS_ENDPOINT)
    private var getServiceNameMethod: RpcMethod<IntrospectionService, Unit, String>? = null
    private var getEndpointsMethod: RpcMethod<IntrospectionService, Unit, List<String>>? = null

    private fun serviceNameMethod(): RpcMethod<IntrospectionService, Unit, String> =
        getServiceNameMethod
            ?: RpcMethod<IntrospectionService, Unit, String>(
                SERVICE_NAME_ENDPOINT,
                SerializerTransformer(Unit.serializer()),
                SerializerTransformer(String.serializer()),
                object : ServiceExecutor {
                    override suspend fun invoke(service: RpcService, input: Any?): Any? =
                        (service as IntrospectionService).getServiceName(input as Unit)
                }
            ).also { getServiceNameMethod = it }

    private fun endpointsMethod(): RpcMethod<IntrospectionService, Unit, List<String>> =
        getEndpointsMethod
            ?: RpcMethod<IntrospectionService, Unit, List<String>>(
                ENDPOINTS_ENDPOINT,
                SerializerTransformer(Unit.serializer()),
                SerializerTransformer(ListSerializer(String.serializer())),
                object : ServiceExecutor {
                    override suspend fun invoke(service: RpcService, input: Any?): Any? =
                        (service as IntrospectionService).getEndpoints(input as Unit)
                }
            ).also { getEndpointsMethod = it }

    override fun <S> createStub(channel: SerializedService<S>): IntrospectionService =
        object : IntrospectionService {
            override suspend fun getServiceName(u: Unit): String =
                serviceNameMethod().callChannel(channel, u) as String
            override suspend fun getEndpoints(u: Unit): List<String> =
                endpointsMethod().callChannel(channel, u) as List<String>

            override suspend fun getIntrospection(u: Unit): IntrospectionService = this

            override suspend fun close() {
                channel.close()
            }
        }

    override fun findEndpoint(endpoint: String): RpcMethod<*, *, *> = when (endpoint) {
        SERVICE_NAME_ENDPOINT -> serviceNameMethod()
        ENDPOINTS_ENDPOINT -> endpointsMethod()
        else -> throw RpcEndpointException("Unknown endpoint $endpoint")
    }
}
