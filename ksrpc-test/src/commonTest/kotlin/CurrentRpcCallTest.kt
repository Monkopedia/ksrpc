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
import com.monkopedia.ksrpc.annotation.KsNotification
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.channels.currentRpcCall
import com.monkopedia.ksrpc.jsonrpc.JsonRpcCallId
import com.monkopedia.ksrpc.jsonrpc.asJsonRpcConnection
import com.monkopedia.ksrpc.packets.internal.PacketCallId
import com.monkopedia.ksrpc.sockets.asConnection
import io.ktor.utils.io.close
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Handler used to capture the [currentRpcCall] element observed inside a dispatched RPC.
 * Tests send the observed id/method back through a [CompletableDeferred] so the assertion
 * can run on the client side after the call returns.
 */
@KsService
interface CallContextProbeService : RpcService {
    @KsMethod("/probe")
    suspend fun probe(value: String): String

    @KsMethod("/nested")
    suspend fun nested(value: String): String

    /**
     * Notification method. Over jsonrpc the call layer emits this as a notification (no `id`)
     * because the method is annotated with [KsNotification]. Used to exercise the
     * "notification id is null" path.
     */
    @KsMethod("/notifyOnly")
    @KsNotification
    suspend fun notifyOnly(value: String)
}

private class CapturingProbe(
    /** Captures the (id, endpoint) observed in the probe handler. */
    val observed: CompletableDeferred<Pair<Any?, String>> = CompletableDeferred(),
    val notifyObserved: CompletableDeferred<Pair<Any?, String>> = CompletableDeferred()
) : CallContextProbeService {
    override suspend fun probe(value: String): String {
        val call = currentRpcCall()
        observed.complete((call?.id to (call?.method?.endpoint ?: "<none>")))
        return "ok:$value"
    }

    override suspend fun nested(value: String): String = "n:$value"

    override suspend fun notifyOnly(value: String) {
        val call = currentRpcCall()
        notifyObserved.complete((call?.id to (call?.method?.endpoint ?: "<none>")))
    }
}

class CurrentRpcCallTest {

    @Test
    fun packetTransportHandlerSeesPacketCallId() = runBlockingUnit {
        val (clientToServer, serverFromClient) = createPipe()
        val (serverToClient, clientFromServer) = createPipe()
        val probe = CapturingProbe()

        val serverConnection = (serverFromClient to serverToClient).asConnection(
            ksrpcEnvironment { }
        )
        val clientConnection = (clientFromServer to clientToServer).asConnection(
            ksrpcEnvironment { }
        )
        val serverJob = launch(Dispatchers.Default) {
            serverConnection.registerDefault(probe.serialized(ksrpcEnvironment { }))
        }
        try {
            val stub = clientConnection.defaultChannel()
                .toStub<CallContextProbeService, String>()
            assertEquals("ok:hi", stub.probe("hi"))
            val (id, method) = withTimeout(5_000) { probe.observed.await() }
            assertNotNull(id, "packet transport handler must see a non-null RpcCallId")
            assertTrue(
                id is PacketCallId,
                "packet transport handler's RpcCallId must be a PacketCallId (was $id)"
            )
            // The method exposed to the handler is the wire endpoint ("/probe"). Consumers
            // treat this as an opaque string; the assertion just pins the concrete value so
            // regressions in propagation are obvious.
            // Method name reaches the dispatcher without the leading `/` — the transport's
            // path stripping happens before dispatch. Pin the observed value so regressions
            // in propagation are obvious without depending on the slash convention.
            assertEquals("probe", method)
        } finally {
            try {
                clientConnection.close()
            } catch (_: Throwable) {
            }
            try {
                serverConnection.close()
            } catch (_: Throwable) {
            }
            serverJob.cancel()
        }
    }

    @Test
    fun jsonRpcTransportHandlerSeesJsonRpcCallId() = runBlockingUnit {
        val probe = CapturingProbe()

        val (output, input) = createPipe()
        val (so, si) = createPipe()
        GlobalScope.launch(Dispatchers.Default) {
            val connection = (input to so).asJsonRpcConnection(
                ksrpcEnvironment { },
                includeContentHeaders = false
            )
            connection.registerDefault(probe.serialized(ksrpcEnvironment { }))
        }
        try {
            val channel = (si to output).asJsonRpcConnection(
                ksrpcEnvironment { },
                includeContentHeaders = false
            )
            val stub = channel.defaultChannel().toStub<CallContextProbeService, String>()
            assertEquals("ok:x", stub.probe("x"))
            val (id, method) = withTimeout(5_000) { probe.observed.await() }
            assertNotNull(id, "jsonrpc call-with-id handler must see a non-null RpcCallId")
            assertTrue(
                id is JsonRpcCallId,
                "jsonrpc transport handler's RpcCallId must be a JsonRpcCallId (was $id)"
            )
            // Method name reaches the dispatcher without the leading `/` — the transport's
            // path stripping happens before dispatch. Pin the observed value so regressions
            // in propagation are obvious without depending on the slash convention.
            assertEquals("probe", method)
        } finally {
            try {
                input.cancel(null)
            } catch (_: Throwable) {
            }
            try {
                si.cancel(null)
            } catch (_: Throwable) {
            }
            output.close(null)
            so.close(null)
        }
    }

    @Test
    fun jsonRpcNotificationHandlerSeesNullId() = runBlockingUnit {
        // For jsonrpc notifications (no `id` on the wire), the handler's
        // `currentRpcCall().id` must be null. Method name must still be populated. The
        // `notifyOnly` RPC flows through the notification path because it is annotated
        // with `@KsNotification`.
        val probe = CapturingProbe()

        val (output, input) = createPipe()
        val (so, si) = createPipe()
        GlobalScope.launch(Dispatchers.Default) {
            val connection = (input to so).asJsonRpcConnection(
                ksrpcEnvironment { },
                includeContentHeaders = false
            )
            connection.registerDefault(probe.serialized(ksrpcEnvironment { }))
        }
        try {
            val channel = (si to output).asJsonRpcConnection(
                ksrpcEnvironment { },
                includeContentHeaders = false
            )
            val stub = channel.defaultChannel().toStub<CallContextProbeService, String>()
            stub.notifyOnly("hello")
            val (id, method) = withTimeout(5_000) { probe.notifyObserved.await() }
            assertNull(id, "notification handler must see a null RpcCallId")
            assertEquals("notifyOnly", method)
        } finally {
            try {
                input.cancel(null)
            } catch (_: Throwable) {
            }
            try {
                si.cancel(null)
            } catch (_: Throwable) {
            }
            output.close(null)
            so.close(null)
        }
    }

    @Test
    fun nestedStubCallDoesNotInheritOuterCallElement() = runBlockingUnit {
        // A handler that spins up a fresh outbound RPC through a stub must not see its own
        // CurrentRpcCallElement inside the nested call. The element is installed by the
        // dispatcher on the receiving side — the stub side should carry a clean context.
        val (clientToServer, serverFromClient) = createPipe()
        val (serverToClient, clientFromServer) = createPipe()

        // Observed from the nested stub's perspective (what the caller-side code runs after
        // the stub method returns), and separately from the inner server-side handler.
        val outerObserved = CompletableDeferred<Any?>()
        val innerObserved = CompletableDeferred<Any?>()

        lateinit var loopbackStub: CallContextProbeService

        val service = object : CallContextProbeService {
            override suspend fun probe(value: String): String {
                if (value == "outer") {
                    val outerCall = currentRpcCall()
                    assertNotNull(outerCall, "outer handler must see its own call element")
                    // Fire a nested call through a stub to the same service. The nested call
                    // should NOT carry the outer call's CurrentRpcCallElement: the stub path
                    // strips it, and the nested inbound dispatch installs a fresh one.
                    val nested = loopbackStub.probe("inner")
                    // Record what the outer handler observed at this point.
                    outerObserved.complete(outerCall.id)
                    return "outer-got:$nested"
                }
                // Inner invocation: record what the handler sees and ensure it's a *new*
                // element (id differs from the outer one).
                val inner = currentRpcCall()
                innerObserved.complete(inner?.id)
                return "inner"
            }

            override suspend fun nested(value: String): String = value

            override suspend fun notifyOnly(value: String) = Unit
        }

        val serverConnection = (serverFromClient to serverToClient).asConnection(
            ksrpcEnvironment { }
        )
        val clientConnection = (clientFromServer to clientToServer).asConnection(
            ksrpcEnvironment { }
        )

        // Loopback connection so the handler (running on the server side) can reach the
        // service via a stub through the client side. We wire the handler's stub to the
        // *client* connection's default channel — calling `probe("inner")` then goes
        // client -> server over the same pipes, triggering a fresh inbound dispatch on the
        // server.
        val serverJob = launch(Dispatchers.Default) {
            serverConnection.registerDefault(service.serialized(ksrpcEnvironment { }))
        }
        try {
            loopbackStub = clientConnection.defaultChannel()
                .toStub<CallContextProbeService, String>()

            // Invoke the outer call.
            val result = loopbackStub.probe("outer")
            assertEquals("outer-got:inner", result)

            val outerId = withTimeout(5_000) { outerObserved.await() }
            val innerId = withTimeout(5_000) { innerObserved.await() }
            assertNotNull(outerId, "outer handler id must not be null")
            assertNotNull(innerId, "inner handler id must not be null")
            assertTrue(
                outerId != innerId,
                "nested inbound call must see a fresh id, not the outer call's ($outerId)"
            )
        } finally {
            try {
                clientConnection.close()
            } catch (_: Throwable) {
            }
            try {
                serverConnection.close()
            } catch (_: Throwable) {
            }
            serverJob.cancel()
        }
    }

    @Test
    fun handlerThatDoesNotQueryElementStillWorks() = runBlockingUnit {
        // Sanity: handlers that never touch currentRpcCall() must continue to work. This
        // guards against the context-installation path accidentally wrapping the handler in
        // something that breaks the existing dispatch contract.
        val (clientToServer, serverFromClient) = createPipe()
        val (serverToClient, clientFromServer) = createPipe()

        val service = object : CallContextProbeService {
            override suspend fun probe(value: String): String = "plain:$value"
            override suspend fun nested(value: String): String = "plain-nested:$value"
            override suspend fun notifyOnly(value: String) = Unit
        }

        val serverConnection = (serverFromClient to serverToClient).asConnection(
            ksrpcEnvironment { }
        )
        val clientConnection = (clientFromServer to clientToServer).asConnection(
            ksrpcEnvironment { }
        )
        val serverJob = launch(Dispatchers.Default) {
            serverConnection.registerDefault(service.serialized(ksrpcEnvironment { }))
        }
        try {
            val stub = clientConnection.defaultChannel()
                .toStub<CallContextProbeService, String>()
            assertEquals("plain:a", stub.probe("a"))
            assertEquals("plain-nested:b", stub.nested("b"))
        } finally {
            try {
                clientConnection.close()
            } catch (_: Throwable) {
            }
            try {
                serverConnection.close()
            } catch (_: Throwable) {
            }
            serverJob.cancel()
        }
    }
}
