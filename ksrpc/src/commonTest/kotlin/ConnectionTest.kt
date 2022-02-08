package com.monkopedia.ksrpc

import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.channels.Connection
import com.monkopedia.ksrpc.channels.asConnection
import com.monkopedia.ksrpc.channels.registerDefault
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun testForward() = executePipe(serviceJob = { c ->
        c.registerDefault<PrimaryInterface>(object : PrimaryInterface {
            override suspend fun basicCall(input: String): String {
                return "Respond: $input"
            }

            override suspend fun childInput(input: ChildInterface): String =
                error("Not implemented")

            override suspend fun childOutput(input: String): ChildInterface =
                error("Not implemented")
        })
    }, clientJob = { c ->
        val service = c.defaultChannel().toStub<PrimaryInterface>()
        assertEquals("Respond: Hello world", service.basicCall("Hello world"))
    })

    @Test
    fun testReverse() = executePipe(serviceJob = { c ->
        val service = c.defaultChannel().toStub<PrimaryInterface>()
        service.basicCall("Hello world")
    }, clientJob = { c ->
        val callComplete = CompletableDeferred<String>()
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
    })

    @Test
    fun testOverlapForward() = executePipe(serviceJob = { c ->
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
    }, clientJob = { c ->
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
    })

    @Test
    fun testOverlapReverse() = executePipe(serviceJob = { c ->
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
    }, clientJob = { c ->
        val service = c.defaultChannel().toStub<PrimaryInterface>()
        val callComplete = CompletableDeferred<String>()
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
    })

    @Test
    fun testServiceOverlapForward() = executePipe(serviceJob = { c ->
        c.registerDefault<PrimaryInterface>(object : PrimaryInterface {
            override suspend fun basicCall(input: String): String = error("Not implemented")

            override suspend fun childInput(input: ChildInterface): String =
                input.rpc("Hello world")

            override suspend fun childOutput(input: String): ChildInterface =
                error("Not implemented")
        })
    }, clientJob = { c ->
        val service = c.defaultChannel().toStub<PrimaryInterface>()
        assertEquals(
            "Client: Hello world",
            service.childInput(object : ChildInterface {
                override suspend fun rpc(input: String): String {
                    return "Client: $input"
                }
            })
        )
    })

    @Test
    fun testServiceOverlapReverse() = executePipe(serviceJob = { c ->
        val clientService = c.defaultChannel().toStub<PrimaryInterface>()
        clientService.childInput(object : ChildInterface {
            override suspend fun rpc(input: String): String {
                return "Respond: $input"
            }
        })
    }, clientJob = { c ->
        val callComplete = CompletableDeferred<String>()
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
    })

    @Test
    fun testReturnServiceForward() = executePipe(serviceJob = { c ->
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
    }, clientJob = { c ->
        val service = c.defaultChannel().toStub<PrimaryInterface>()
        val firstService = service.childOutput("First service")
        val secondService = service.childOutput("Second service")
        assertEquals("First service: Hello world", firstService.rpc("Hello world"))
        assertEquals("Second service: Hello trees", secondService.rpc("Hello trees"))
        assertEquals("First service: Hello trees", firstService.rpc("Hello trees"))
    })

    @Test
    fun testReturnServiceReverse() = executePipe(serviceJob = { c ->
        val service = c.defaultChannel().toStub<PrimaryInterface>()
        val firstService = service.childOutput("First service")
        val secondService = service.childOutput("Second service")
        assertEquals("First service: Hello world", firstService.rpc("Hello world"))
        assertEquals("Second service: Hello trees", secondService.rpc("Hello trees"))
        assertEquals("First service: Hello trees", firstService.rpc("Hello trees"))
        service.basicCall("Done")
    }, clientJob = { c ->
        val callComplete = CompletableDeferred<String>()
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
    })

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
