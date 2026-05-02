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
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.Serializable

/**
 * Tests for nested generic introspection chains:
 * `OuterService<T> -> InnerService<T> -> Flow<T>`
 *
 * Verifies that type arguments propagate correctly through sub-service
 * boundaries and that introspection reports the right typeArgs at each level.
 *
 * Uses concrete (non-generic) service interfaces that specialize generic
 * parents, since generic @KsService companions are RpcObjectFactory (not
 * RpcObject) and require concrete instantiation.
 */
class NestedGenericIntrospectionTest {

    @Serializable
    data class Update(val msg: String)

    @KsService
    @KsIntrospectable
    interface StreamService : IntrospectableRpcService {
        @KsMethod("/stream")
        suspend fun stream(input: String): Flow<Update>
    }

    @KsService
    @KsIntrospectable
    interface NestedService : IntrospectableRpcService {
        @KsMethod("/child")
        suspend fun child(input: String): StreamService
    }

    private val streamImpl = object : StreamService {
        override suspend fun stream(input: String): Flow<Update> = emptyFlow()
    }

    private val nestedImpl = object : NestedService {
        override suspend fun child(input: String): StreamService = streamImpl
    }

    @Test
    fun nestedServiceChildEndpointIsService() = runBlockingUnit {
        val channel =
            nestedImpl.serialized<NestedService, String>(ksrpcEnvironment { })
        val stub = channel.toStub<NestedService, String>()
        val introspection = stub.getIntrospection()
        val info = introspection.getEndpointInfo("child")
        val output = info.output
        assertTrue(
            output is RpcDataType.Service,
            "expected /child output to be RpcDataType.Service, got $output"
        )
    }

    @Test
    fun streamEndpointHasFlowTypeArgs() = runBlockingUnit {
        val channel =
            streamImpl.serialized<StreamService, String>(ksrpcEnvironment { })
        val stub = channel.toStub<StreamService, String>()
        val introspection = stub.getIntrospection()
        val info = introspection.getEndpointInfo("stream")
        val output = info.output
        assertTrue(
            output is RpcDataType.Service,
            "expected /stream output to be RpcDataType.Service (KsFlowService), got $output"
        )
        assertEquals(
            "com.monkopedia.ksrpc.flow.KsFlowService",
            output.qualifiedName,
            "expected KsFlowService for Flow<Update>"
        )
        assertEquals(
            1,
            output.typeArgs.size,
            "expected 1 type arg on KsFlowService<Update>, got ${output.typeArgs}"
        )
        val typeArg = output.typeArgs[0]
        assertTrue(
            typeArg is RpcDataType.DataStructure,
            "expected type arg to be DataStructure for Update, got $typeArg"
        )
    }

    @Test
    fun nestedChainIntrospectionResolves() = runBlockingUnit {
        val channel =
            nestedImpl.serialized<NestedService, String>(ksrpcEnvironment { })
        val stub = channel.toStub<NestedService, String>()
        val introspection = stub.getIntrospection()

        // Verify the child endpoint returns a Service type
        val childInfo = introspection.getEndpointInfo("child")
        val childOutput = childInfo.output
        assertTrue(childOutput is RpcDataType.Service)

        // Navigate to the child's introspection
        val childIntrospection = introspection.getIntrospectionFor(childOutput.qualifiedName)
        val streamInfo = childIntrospection.getEndpointInfo("stream")
        val streamOutput = streamInfo.output

        // The stream endpoint should be KsFlowService with Update type arg
        assertTrue(
            streamOutput is RpcDataType.Service,
            "expected nested /stream to be RpcDataType.Service, got $streamOutput"
        )
        assertEquals(
            "com.monkopedia.ksrpc.flow.KsFlowService",
            streamOutput.qualifiedName
        )
        assertEquals(
            1,
            streamOutput.typeArgs.size,
            "expected 1 type arg on nested KsFlowService<Update>"
        )
        assertTrue(
            streamOutput.typeArgs[0] is RpcDataType.DataStructure,
            "expected DataStructure for Update type arg"
        )
    }
}
