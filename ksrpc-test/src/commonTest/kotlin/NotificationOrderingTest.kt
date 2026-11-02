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
import com.monkopedia.ksrpc.jsonrpc.asJsonRpcConnection
import com.monkopedia.ksrpc.sockets.asConnection
import io.ktor.utils.io.close
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield

@KsService
interface OrderingProbeService : RpcService {
    @KsMethod("/note")
    @KsNotification
    suspend fun note(seq: Int)
}

private class RecordingProbe(private val expected: Int) : OrderingProbeService {
    private val lock = Mutex()
    val seen = mutableListOf<Int>()
    val done = CompletableDeferred<Unit>()

    override suspend fun note(seq: Int) {
        // One suspension point before recording, so handlers that were started concurrently
        // can interleave. This is the shape that exposed the reordering in practice: a
        // non-suspending handler showed it rarely, a handler with one yield() far more often.
        yield()
        lock.withLock {
            seen += seq
            if (seen.size == expected) done.complete(Unit)
        }
    }
}

/**
 * Notifications sent in order must be handled in order, on every transport (#280).
 *
 * Fire-and-forget delivery made the sender's order the only order there is: nothing on the
 * receiving side can recover it afterwards, and a consumer applying ordered edits — an
 * editor's change notifications being the obvious case — silently corrupts its state when two
 * of them swap.
 */
@OptIn(DelicateCoroutinesApi::class)
class NotificationOrderingTest {

    private val count = 300

    @Test
    fun jsonRpcDeliversNotificationsInTheOrderTheyWereSent() = runBlockingUnit {
        val probe = RecordingProbe(count)
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
            val stub = channel.defaultChannel().toStub<OrderingProbeService, String>()
            repeat(count) { stub.note(it) }
            withTimeout(20_000) { probe.done.await() }
            assertEquals(
                (0 until count).toList(),
                probe.seen,
                "notifications were handled out of the order they were sent"
            )
        } finally {
            runCatching { input.cancel(null) }
            runCatching { si.cancel(null) }
            output.close(null)
            so.close(null)
        }
    }

    /**
     * The packet transport has no notification semantics: a `@KsNotification` call is an
     * ordinary request there, and the caller waits for its reply before sending the next. So
     * order is already a consequence of the sender awaiting each call, and this pins that.
     */
    @Test
    fun packetTransportDeliversNotificationsInTheOrderTheyWereSent() = runBlockingUnit {
        val probe = RecordingProbe(count)
        val (clientToServer, serverFromClient) = createPipe()
        val (serverToClient, clientFromServer) = createPipe()
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
                .toStub<OrderingProbeService, String>()
            repeat(count) { stub.note(it) }
            withTimeout(20_000) { probe.done.await() }
            assertEquals(
                (0 until count).toList(),
                probe.seen,
                "notifications were handled out of the order they were sent"
            )
        } finally {
            runCatching { clientConnection.close() }
            runCatching { serverConnection.close() }
            serverJob.cancel()
        }
    }
}
