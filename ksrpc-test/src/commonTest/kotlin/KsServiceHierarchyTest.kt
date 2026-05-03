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

import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.channels.Connection
import com.monkopedia.ksrpc.sockets.asConnection
import io.ktor.utils.io.close
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch

// --- Service hierarchy definitions ---

@KsService
interface HierarchyCoreService : RpcService {
    @KsMethod("/getData")
    suspend fun getData(id: String): String
}

@KsService
interface HierarchyExtendedService : HierarchyCoreService, RpcBidiService {
    @KsMethod("/updates")
    suspend fun updates(filter: String): Flow<String>
}

@KsService
interface HierarchyBaseService : RpcService {
    @KsMethod("/base")
    suspend fun base(): String
}

@KsService
interface HierarchyMiddleService : HierarchyBaseService, RpcHostService {
    @KsMethod("/middle")
    suspend fun middle(): HierarchyCoreService
}

@KsService
interface HierarchyFullService : HierarchyMiddleService, RpcBidiService {
    @KsMethod("/stream")
    suspend fun stream(): Flow<String>
}

@KsService
interface HierarchyParentService : RpcHostService {
    @KsMethod("/getExtended")
    suspend fun getExtended(): HierarchyExtendedService
}

// --- Implementations ---

private class HierarchyExtendedServiceImpl : HierarchyExtendedService {
    override suspend fun getData(id: String): String = "data-$id"
    override suspend fun updates(filter: String): Flow<String> = flowOf("update-1")
}

private class HierarchyFullServiceImpl : HierarchyFullService {
    override suspend fun base(): String = "base-value"
    override suspend fun middle(): HierarchyCoreService = object : HierarchyCoreService {
        override suspend fun getData(id: String): String = "middle-data-$id"
    }
    override suspend fun stream(): Flow<String> = flowOf("stream-a", "stream-b")
}

/**
 * Integration tests for @KsService interface inheritance (hierarchy support).
 *
 * These tests verify that a @KsService interface can extend another @KsService interface,
 * inheriting parent methods while adding its own. Currently this feature is NOT implemented
 * and these tests are expected to FAIL. They document the intended behavior for the feature.
 */
class KsServiceHierarchyTest {

    // --- Test 1: Basic inheritance - child includes parent methods ---

    @Test
    fun extendedServiceEndpointsContainBothParentAndChild() = runBlockingUnit {
        val obj = rpcObject<HierarchyExtendedService>()
        assertTrue(
            "getData" in obj.endpoints,
            "Expected 'getData' (inherited) in endpoints: ${obj.endpoints}"
        )
        assertTrue(
            "updates" in obj.endpoints,
            "Expected 'updates' (own) in endpoints: ${obj.endpoints}"
        )
    }

    @Test
    fun extendedServiceRoundTripGetData() = executePipe(
        serviceJob = { c ->
            val impl = HierarchyExtendedServiceImpl()
            c.registerDefault(impl.serialized<HierarchyExtendedService, String>(c.env))
        },
        clientJob = { c ->
            val stub = c.defaultChannel().toStub<HierarchyExtendedService, String>()
            assertEquals("data-abc", stub.getData("abc"))
        }
    )

    @Test
    fun extendedServiceRoundTripUpdates() = executePipe(
        serviceJob = { c ->
            val impl = HierarchyExtendedServiceImpl()
            c.registerDefault(impl.serialized<HierarchyExtendedService, String>(c.env))
        },
        clientJob = { c ->
            val stub = c.defaultChannel().toStub<HierarchyExtendedService, String>()
            val result = stub.updates("filter").toList()
            assertEquals(listOf("update-1"), result)
        }
    )

    // --- Test 2: Parent works independently ---

    @Test
    fun coreServiceWorksIndependently() = runBlockingUnit {
        val obj = rpcObject<HierarchyCoreService>()
        assertTrue(
            "getData" in obj.endpoints,
            "Expected 'getData' in core service endpoints: ${obj.endpoints}"
        )
    }

    @Test
    fun coreServiceRoundTripIndependently() = runBlockingUnit {
        val impl: HierarchyCoreService = object : HierarchyCoreService {
            override suspend fun getData(id: String): String = "core-$id"
        }
        val channel = impl.serialized<HierarchyCoreService, String>(ksrpcEnvironment { })
        val stub = channel.toStub<HierarchyCoreService, String>()
        assertEquals("core-xyz", stub.getData("xyz"))
    }

    // --- Test 3: Implementation class resolves correctly ---

    @Test
    fun implementationClassSerializesAndCallsViaStub() = executePipe(
        serviceJob = { c ->
            val impl = HierarchyExtendedServiceImpl()
            c.registerDefault(impl.serialized<HierarchyExtendedService, String>(c.env))
        },
        clientJob = { c ->
            val stub = c.defaultChannel().toStub<HierarchyExtendedService, String>()
            assertEquals("data-test", stub.getData("test"))
            val updates = stub.updates("f").toList()
            assertEquals(listOf("update-1"), updates)
        }
    )

    // --- Test 4: Linear chain (A -> B -> C) ---

    @Test
    fun fullServiceEndpointsContainAllThree() = runBlockingUnit {
        val obj = rpcObject<HierarchyFullService>()
        assertTrue(
            "base" in obj.endpoints,
            "Expected 'base' (from BaseService) in endpoints: ${obj.endpoints}"
        )
        assertTrue(
            "middle" in obj.endpoints,
            "Expected 'middle' (from MiddleService) in endpoints: ${obj.endpoints}"
        )
        assertTrue(
            "stream" in obj.endpoints,
            "Expected 'stream' (own) in endpoints: ${obj.endpoints}"
        )
    }

    @Test
    fun fullServiceLinearChainRoundTrip() = executePipe(
        serviceJob = { c ->
            val impl = HierarchyFullServiceImpl()
            c.registerDefault(impl.serialized<HierarchyFullService, String>(c.env))
        },
        clientJob = { c ->
            val stub = c.defaultChannel().toStub<HierarchyFullService, String>()
            assertEquals("base-value", stub.base())
            val streams = stub.stream().toList()
            assertEquals(listOf("stream-a", "stream-b"), streams)
        }
    )

    @Test
    fun fullServiceSubserviceFromMiddle() = executePipe(
        serviceJob = { c ->
            val impl = HierarchyFullServiceImpl()
            c.registerDefault(impl.serialized<HierarchyFullService, String>(c.env))
        },
        clientJob = { c ->
            val stub = c.defaultChannel().toStub<HierarchyFullService, String>()
            val child = stub.middle()
            assertEquals("middle-data-hello", child.getData("hello"))
        }
    )

    // --- Test 6: Transport compatibility (PIPE) ---

    @Test
    fun extendedServiceWorksOverPipeTransport() = executePipe(
        serviceJob = { c ->
            val impl = HierarchyExtendedServiceImpl()
            c.registerDefault(impl.serialized<HierarchyExtendedService, String>(c.env))
        },
        clientJob = { c ->
            val stub = c.defaultChannel().toStub<HierarchyExtendedService, String>()
            // Verify both inherited and own methods work over pipe
            assertEquals("data-pipe", stub.getData("pipe"))
            val updates = stub.updates("pipe-filter").toList()
            assertEquals(listOf("update-1"), updates)
        }
    )

    // --- Test 7: Sub-service returning a hierarchy service ---

    @Test
    fun parentServiceReturnsHierarchySubservice() = executePipe(
        serviceJob = { c ->
            val impl = object : HierarchyParentService {
                override suspend fun getExtended(): HierarchyExtendedService =
                    HierarchyExtendedServiceImpl()
            }
            c.registerDefault(impl.serialized<HierarchyParentService, String>(c.env))
        },
        clientJob = { c ->
            val stub = c.defaultChannel().toStub<HierarchyParentService, String>()
            val extended = stub.getExtended()
            assertEquals("data-sub", extended.getData("sub"))
            val updates = extended.updates("sub-filter").toList()
            assertEquals(listOf("update-1"), updates)
        }
    )

    // --- Pipe test helper ---

    private fun executePipe(
        serviceJob: suspend (Connection<String>) -> Unit,
        clientJob: suspend (Connection<String>) -> Unit
    ) = runBlockingUnit {
        val (output, input) = createPipe()
        val (so, si) = createPipe()
        val serviceChannel = (si to output).asConnection(
            ksrpcEnvironment {
                errorListener = ErrorListener { }
            }
        )
        val clientChannel = (input to so).asConnection(
            ksrpcEnvironment {
                errorListener = ErrorListener { }
            }
        )
        val bgJob = GlobalScope.launch(Dispatchers.Default) {
            serviceJob(serviceChannel)
        }
        try {
            clientJob(clientChannel)
        } finally {
            try {
                clientChannel.close()
            } catch (_: Throwable) {}
            try {
                serviceChannel.close()
            } catch (_: Throwable) {}
            try {
                input.cancel(null)
            } catch (_: Throwable) {}
            try {
                si.cancel(null)
            } catch (_: Throwable) {}
            output.close(null)
            so.close(null)
            bgJob.join()
        }
    }
}

/**
 * Transport functionality test: verifies ExtendedService works over all transport types.
 * This is the test for scenario 6 (transport compatibility) using the RpcFunctionalityTest
 * pattern which exercises SERIALIZE, PIPE, HTTP, and WEBSOCKET transports.
 */
class KsServiceHierarchyTransportTest :
    RpcFunctionalityTest(
        serializedChannel = {
            val impl = object : HierarchyExtendedService {
                override suspend fun getData(id: String): String = "data-$id"
                override suspend fun updates(filter: String): Flow<String> =
                    flowOf("update-$filter")
            }
            impl.serialized(ksrpcEnvironment { })
        },
        verifyOnChannel = { serializedChannel ->
            val stub = serializedChannel.toStub<HierarchyExtendedService, String>()
            assertEquals("data-transport", stub.getData("transport"))
        },
        // Only PIPE supports bidi (needed for Flow), but getData works on all
        supportedTypes = listOf(TestType.PIPE)
    )
