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
internal const val ENDPOINT_INFO_ENDPOINT = "endpoint_info"
internal const val INTROSPECTION_FOR_ENDPOINT = "introspection_for"

@PublishedApi
internal data class IntrospectionServiceImpl(private val rpcObject: RpcObject<*>) :
    IntrospectionService {
    override suspend fun getServiceName(u: Unit): String = rpcObject.serviceName

    override suspend fun getEndpoints(u: Unit): List<String> = rpcObject.endpoints

    override suspend fun getEndpointInfo(endpoint: String): RpcEndpointInfo {
        val normalized = endpoint.trimStart('/')
        val method = rpcObject.findEndpoint(normalized)
        return RpcEndpointInfo(
            endpoint = normalized,
            input = method.inputRpcDataType(),
            output = method.outputRpcDataType()
        )
    }

    private val subserviceRpcObjects: Map<String, RpcObject<*>> by lazy {
        rpcObject.endpoints.flatMap { endpoint ->
            val method = rpcObject.findEndpoint(endpoint)
            method.findSubserviceTransformers()
        }.associate {
            it.serviceObject.serviceName to it.serviceObject
        }
    }

    override suspend fun getIntrospectionFor(service: String): IntrospectionService {
        val normalized = service.trim()
        val serviceObject = subserviceRpcObjects[normalized]
            ?: throw RpcEndpointException("Unknown introspection target $service")
        return IntrospectionServiceImpl(serviceObject)
    }
}
