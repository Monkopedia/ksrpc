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
import com.monkopedia.ksrpc.sockets.asConnection
import io.ktor.utils.io.close
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.supervisorScope

@KsService
interface MultiChannelRaceService : RpcService {
    @KsMethod("/slow")
    suspend fun slow(input: String): String
}

class MultiChannelRaceTest {

    @Test
    fun testNoMultiChannelFailureWhenResponseArrivesAfterClose() = runBlockingUnit {
        var observedLegacyFailure = false

        for (i in 0 until 200) {
            supervisorScope {
                val connectionErrors = Channel<Throwable>(Channel.UNLIMITED)
                val serverStarted = CompletableDeferred<Unit>()
                val returnValue = CompletableDeferred<String>()

                val env = ksrpcEnvironment {
                    errorListener = ErrorListener { t ->
                        connectionErrors.trySend(t).onFailure {}
                    }
                    logger = object : Logger {
                        override fun warn(tag: String, message: String, throwable: Throwable?) {
                            if (tag == "MultiChannel" && throwable != null) {
                                connectionErrors.trySend(throwable).onFailure {}
                            }
                        }
                    }
                }

                val (serverWrite, clientRead) = createPipe()
                val (clientWrite, serverRead) = createPipe()
                val server = (serverRead to serverWrite).asConnection(env)
                val client = (clientRead to clientWrite).asConnection(env)

                try {
                    server.registerDefault(
                        (object : MultiChannelRaceService {
                            override suspend fun slow(input: String): String {
                                serverStarted.complete(Unit)
                                return returnValue.await()
                            }
                        }).serialized(env)
                    )

                    val call = launch(Dispatchers.Default) {
                        try {
                            client.defaultChannel().toStub<MultiChannelRaceService, String>().slow("payload")
                        } catch (_: CancellationException) {
                        } catch (_: Throwable) {
                        }
                    }

                    withTimeout(1_000.milliseconds) {
                        serverStarted.await()
                    }
                    runCatching { client.close() }
                    returnValue.complete("response")
                    withTimeout(1_000.milliseconds) {
                        call.join()
                    }

                    val captured = mutableListOf<Throwable>()
                    while (true) {
                        val error = connectionErrors.tryReceive().getOrNull() ?: break
                        captured.add(error)
                    }
                    if (captured.any {
                        it is IllegalStateException && it.message?.contains("MultiChannel") == true
                    }) {
                        observedLegacyFailure = true
                    }
                } finally {
                    runCatching { client.close() }
                    runCatching { server.close() }
                    runCatching { clientRead.cancel(null) }
                    runCatching { clientWrite.close(null) }
                    runCatching { serverRead.cancel(null) }
                    runCatching { serverWrite.close(null) }
                }
            }
        }

        assertFalse(observedLegacyFailure, "Observed legacy MultiChannel closed exception")
    }
}
