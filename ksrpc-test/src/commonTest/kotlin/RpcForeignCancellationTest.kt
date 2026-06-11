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
import com.monkopedia.ksrpc.channels.registerDefault
import com.monkopedia.ksrpc.sockets.asConnection
import io.ktor.utils.io.close
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

@KsService
interface ForeignCancelInterface : RpcService {
    @KsMethod("/normal")
    suspend fun normal(input: String): String

    @KsMethod("/foreign_cancel")
    suspend fun foreignCancel(input: String): String
}

/**
 * Regression test for #228: a foreign [CancellationException] thrown by a hosted service method
 * — modelling a downstream/peer connection dying and surfacing its `MultiChannel.close`
 * cancellation through a bridging sub-service — must NOT tear down the hosting connection. It
 * should be isolated to that one call's error response, leaving every other service multiplexed
 * on the same connection still callable.
 *
 * Before the fix, `HostSerializedChannelImpl.call` rethrew every [CancellationException]
 * (including foreign ones), which propagated into the hosting connection's receive loop and
 * closed its `MultiChannel` — so the second, unrelated call below failed with
 * "MultiChannel is closed".
 */
class RpcForeignCancellationTest {

    @Test
    fun foreignCancellationDoesNotTearDownConnection() = runBlockingUnit {
        if (RpcFunctionalityTest.TestType.PIPE !in RpcFunctionalityTest.TestType.values()) {
            return@runBlockingUnit
        }
        val (output, input) = createPipe()
        val (so, si) = createPipe()
        val serviceChannel = (si to output).asConnection(
            ksrpcEnvironment {
                errorListener = ErrorListener { /* expected: the foreign cancellation */ }
            }
        )
        val clientChannel = (input to so).asConnection(
            ksrpcEnvironment {
                errorListener = ErrorListener { /* swallow for the test */ }
            }
        )
        val bgJob = GlobalScope.launch(Dispatchers.Default) {
            serviceChannel.registerDefault<ForeignCancelInterface, String>(
                object : ForeignCancelInterface {
                    override suspend fun normal(input: String): String = "ok: $input"

                    override suspend fun foreignCancel(input: String): String =
                        throw CancellationException("Multi-channel failure")
                }
            )
        }
        try {
            withTimeout(30_000) {
                val service =
                    clientChannel.defaultChannel().toStub<ForeignCancelInterface, String>()
                // 1. The foreign-cancellation call surfaces as an error to the caller...
                assertFails { service.foreignCancel("boom") }
                // 2. ...but the connection survives: an unrelated call still works.
                assertEquals("ok: hello", service.normal("hello"))
            }
        } finally {
            try {
                input.cancel(null)
            } catch (t: Throwable) {
            }
            try {
                si.cancel(null)
            } catch (t: Throwable) {
            }
            output.close(null)
            so.close(null)
            bgJob.join()
        }
    }
}
