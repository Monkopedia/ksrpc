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

import com.monkopedia.ksrpc.annotation.KsIntrospectable
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable

/**
 * Regression tests for issue #68 — introspection of methods returning
 * `Flow<T>` must surface the underlying `KsFlowService` sub-service rather
 * than leaking the adapter transformer class name. Since
 * `FlowSubserviceTransformer` shares the `BaseSubserviceTransformer` base
 * with plain `SubserviceTransformer`, introspection recovers the real
 * sub-service name directly; prior to this, `getEndpointInfo(...)` returned
 * `RpcDataType.Service("FlowTransformer")` and a subsequent
 * `getIntrospectionFor(...)` on that name failed — callers had no way to
 * recover the real sub-service.
 */
class FlowIntrospectionTest {

    @Serializable
    data class Update(val value: String)

    @KsService
    @KsIntrospectable
    interface FlowIntrospectionService : IntrospectableRpcService, RpcBidiService {
        @KsMethod("/updates")
        suspend fun updates(filter: String): Flow<Update>
    }

    private val impl: FlowIntrospectionService = object : FlowIntrospectionService {
        override suspend fun updates(filter: String): Flow<Update> = flow {
            emit(Update("$filter-a"))
        }
    }

    @Test
    fun flowReturnEndpointInfoReportsKsFlowServiceName() = runBlockingUnit {
        val channel = impl.serialized<FlowIntrospectionService, String>(ksrpcEnvironment { })
        val stub = channel.toStub<FlowIntrospectionService, String>()
        val introspection = stub.getIntrospection()
        val info = introspection.getEndpointInfo("updates")
        val output = info.output
        assertTrue(
            output is RpcDataType.Service,
            "expected Flow<Update> to surface as RpcDataType.Service, got $output"
        )
        assertEquals(
            "com.monkopedia.ksrpc.flow.KsFlowService",
            output.qualifiedName
        )
    }

    @Test
    fun flowReturnEndpointInfoCarriesTypeArgs() = runBlockingUnit {
        val channel = impl.serialized<FlowIntrospectionService, String>(ksrpcEnvironment { })
        val stub = channel.toStub<FlowIntrospectionService, String>()
        val introspection = stub.getIntrospection()
        val info = introspection.getEndpointInfo("updates")
        val output = info.output
        assertTrue(
            output is RpcDataType.Service,
            "expected Flow<Update> to surface as RpcDataType.Service, got $output"
        )
        assertEquals(1, output.typeArgs.size, "expected 1 type arg for KsFlowService<Update>")
        val typeArg = output.typeArgs[0]
        assertTrue(
            typeArg is RpcDataType.DataStructure,
            "expected type arg to be DataStructure, got $typeArg"
        )
    }

    @Test
    fun flowReturnGetIntrospectionForResolvesKsFlowService() = runBlockingUnit {
        val channel = impl.serialized<FlowIntrospectionService, String>(ksrpcEnvironment { })
        val stub = channel.toStub<FlowIntrospectionService, String>()
        val introspection = stub.getIntrospection()
        val child = introspection.getIntrospectionFor(
            "com.monkopedia.ksrpc.flow.KsFlowService"
        )
        assertEquals(
            "com.monkopedia.ksrpc.flow.KsFlowService",
            child.getServiceName()
        )
        val childEndpoints = child.getEndpoints()
        assertTrue(
            "collect" in childEndpoints,
            "expected KsFlowService to expose 'collect' endpoint, got $childEndpoints"
        )
    }
}
