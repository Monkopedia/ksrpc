/*
 * Copyright 2021 Jason Monk
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
import com.monkopedia.ksrpc.channels.registerDefault
import com.monkopedia.ksrpc.sockets.asConnection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@KsService
interface ChildInterface : RpcService {
    @KsMethod("/rpc")
    suspend fun rpc(input: String): String
}

@KsService
interface PrimaryInterface : RpcService {
    @KsMethod("/rpc")
    suspend fun basicCall(input: String): String

    @KsMethod("/child_input")
    suspend fun childInput(input: ChildInterface): String

    @KsMethod("/child_output")
    suspend fun childOutput(input: String): ChildInterface
}

class ConnectionTest {
    private val supportedTypes: List<RpcFunctionalityTest.TestType> =
        RpcFunctionalityTest.TestType.values().toList()

    @Test
    fun testForward() = executePipe(
        serviceJob = { c ->
            c.registerDefault<PrimaryInterface>(object : PrimaryInterface {
                override suspend fun basicCall(input: String): String {
                    return "Respond: $input"
                }

                override suspend fun childInput(input: ChildInterface): String =
                    error("Not implemented")

                override suspend fun childOutput(input: String): ChildInterface =
                    error("Not implemented")
            })
        },
        clientJob = { c ->
            val service = c.defaultChannel().toStub<PrimaryInterface>()
            assertEquals("Respond: Hello world", service.basicCall("Hello world"))
        }
    )

    private var pendingFinish: CompletableDeferred<Unit>? = null

    @Test
    fun testReverse() = executePipe(
        serviceJob = { c ->
            val service = c.defaultChannel().toStub<PrimaryInterface>()
            service.basicCall("Hello world")
            pendingFinish?.complete(Unit)
        },
        clientJob = { c ->
            val callComplete = CompletableDeferred<String>()
            pendingFinish = CompletableDeferred()
            c.registerDefault<PrimaryInterface>(object : PrimaryInterface {
                override suspend fun basicCall(input: String): String {
                    callComplete.complete(input)
                    return "Respond: $input"
                }

                override suspend fun childInput(input: ChildInterface): String =
                    error("Not implemented")

                override suspend fun childOutput(input: String): ChildInterface =
                    error("Not implemented")
            })
            assertEquals("Hello world", callComplete.await())
            pendingFinish?.await()
        }
    )

    @Test
    fun testOverlapForward() = executePipe(
        serviceJob = { c ->
            val clientService = c.defaultChannel().toStub<PrimaryInterface>()
            c.registerDefault<PrimaryInterface>(object : PrimaryInterface {
                override suspend fun basicCall(input: String): String {
                    return "Respond: ${clientService.basicCall(input)}"
                }

                override suspend fun childInput(input: ChildInterface): String =
                    error("Not implemented")

                override suspend fun childOutput(input: String): ChildInterface =
                    error("Not implemented")
            })
        },
        clientJob = { c ->
            val service = c.defaultChannel().toStub<PrimaryInterface>()
            c.registerDefault<PrimaryInterface>(object : PrimaryInterface {
                override suspend fun basicCall(input: String): String {
                    return "Client: $input"
                }

                override suspend fun childInput(input: ChildInterface): String =
                    error("Not implemented")

                override suspend fun childOutput(input: String): ChildInterface =
                    error("Not implemented")
            })
            assertEquals("Respond: Client: Hello world", service.basicCall("Hello world"))
        }
    )

    @Test
    fun testOverlapReverse() = executePipe(
        serviceJob = { c ->
            val clientService = c.defaultChannel().toStub<PrimaryInterface>()
            c.registerDefault<PrimaryInterface>(object : PrimaryInterface {
                override suspend fun basicCall(input: String): String {
                    return "Respond: $input"
                }

                override suspend fun childInput(input: ChildInterface): String =
                    error("Not implemented")

                override suspend fun childOutput(input: String): ChildInterface =
                    error("Not implemented")
            })
            clientService.basicCall("Hello world")
            pendingFinish?.complete(Unit)
        },
        clientJob = { c ->
            val service = c.defaultChannel().toStub<PrimaryInterface>()
            val callComplete = CompletableDeferred<String>()
            pendingFinish = CompletableDeferred()
            c.registerDefault<PrimaryInterface>(object : PrimaryInterface {
                override suspend fun basicCall(input: String): String {
                    return "Client: ${service.basicCall(input)}".also { callComplete.complete(it) }
                }

                override suspend fun childInput(input: ChildInterface): String =
                    error("Not implemented")

                override suspend fun childOutput(input: String): ChildInterface =
                    error("Not implemented")
            })
            assertEquals("Client: Respond: Hello world", callComplete.await())
            pendingFinish?.await()
        }
    )

    @Test
    fun testServiceOverlapForward() = executePipe(
        serviceJob = { c ->
            c.registerDefault<PrimaryInterface>(object : PrimaryInterface {
                override suspend fun basicCall(input: String): String = error("Not implemented")

                override suspend fun childInput(input: ChildInterface): String =
                    input.rpc("Hello world")

                override suspend fun childOutput(input: String): ChildInterface =
                    error("Not implemented")
            })
        },
        clientJob = { c ->
            val service = c.defaultChannel().toStub<PrimaryInterface>()
            assertEquals(
                "Client: Hello world",
                service.childInput(object : ChildInterface {
                    override suspend fun rpc(input: String): String {
                        return "Client: $input"
                    }
                })
            )
        }
    )

    @Test
    fun testServiceOverlapReverse() = executePipe(
        serviceJob = { c ->
            val clientService = c.defaultChannel().toStub<PrimaryInterface>()
            clientService.childInput(object : ChildInterface {
                override suspend fun rpc(input: String): String {
                    return "Respond: $input"
                }
            })
            pendingFinish?.complete(Unit)
        },
        clientJob = { c ->
            val callComplete = CompletableDeferred<String>()
            pendingFinish = CompletableDeferred()
            c.registerDefault<PrimaryInterface>(object : PrimaryInterface {
                override suspend fun basicCall(input: String): String = error("Not implemented")

                override suspend fun childInput(input: ChildInterface): String =
                    input.rpc("Hello world").also {
                        callComplete.complete(it)
                    }

                override suspend fun childOutput(input: String): ChildInterface =
                    error("Not implemented")
            })
            assertEquals("Respond: Hello world", callComplete.await())
            pendingFinish?.await()
        }
    )

    @Test
    fun testReturnServiceForward() = executePipe(
        serviceJob = { c ->
            c.registerDefault<PrimaryInterface>(object : PrimaryInterface {
                override suspend fun basicCall(input: String): String = error("Not implemented")

                override suspend fun childInput(input: ChildInterface): String =
                    error("Not implemented")

                override suspend fun childOutput(prefix: String): ChildInterface =
                    object : ChildInterface {
                        override suspend fun rpc(input: String): String {
                            return "$prefix: $input"
                        }
                    }
            })
        },
        clientJob = { c ->
            val service = c.defaultChannel().toStub<PrimaryInterface>()
            val firstService = service.childOutput("First service")
            val secondService = service.childOutput("Second service")
            assertEquals("First service: Hello world", firstService.rpc("Hello world"))
            assertEquals("Second service: Hello trees", secondService.rpc("Hello trees"))
            assertEquals("First service: Hello trees", firstService.rpc("Hello trees"))
        }
    )

    @Test
    fun testReturnServiceReverse() = executePipe(
        serviceJob = { c ->
            val service = c.defaultChannel().toStub<PrimaryInterface>()
            val firstService = service.childOutput("First service")
            val secondService = service.childOutput("Second service")
            assertEquals("First service: Hello world", firstService.rpc("Hello world"))
            assertEquals("Second service: Hello trees", secondService.rpc("Hello trees"))
            assertEquals("First service: Hello trees", firstService.rpc("Hello trees"))
            service.basicCall("Done")
            pendingFinish?.complete(Unit)
        },
        clientJob = { c ->
            val callComplete = CompletableDeferred<String>()
            pendingFinish = CompletableDeferred()
            c.registerDefault<PrimaryInterface>(object : PrimaryInterface {
                override suspend fun basicCall(input: String): String =
                    input.also { callComplete.complete(input) }

                override suspend fun childInput(input: ChildInterface): String =
                    error("Not implemented")

                override suspend fun childOutput(prefix: String): ChildInterface =
                    object : ChildInterface {
                        override suspend fun rpc(input: String): String {
                            return "$prefix: $input"
                        }
                    }
            })
            assertEquals("Done", callComplete.await())
            pendingFinish?.await()
        }
    )

    private fun executePipe(
        serviceJob: suspend (Connection) -> Unit,
        clientJob: suspend (Connection) -> Unit
    ) = runBlockingUnit {
        if (RpcFunctionalityTest.TestType.PIPE !in supportedTypes) return@runBlockingUnit
        val (output, input) = createPipe()
        val (so, si) = createPipe()
        val serviceChannel = (si to output).asConnection(
            ksrpcEnvironment {
                errorListener = ErrorListener { t ->
                    t.printStackTrace()
                }
            }
        )
        val clientChannel = (input to so).asConnection(
            ksrpcEnvironment {
                errorListener = ErrorListener { t ->
                    t.printStackTrace()
                }
            }
        )
        val bgJob = GlobalScope.launch(Dispatchers.Default) {
            serviceJob(serviceChannel)
        }
        try {
            clientJob(clientChannel)
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
