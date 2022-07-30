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

import com.monkopedia.ksrpc.channels.DEFAULT_CONTENT_TYPE
import com.monkopedia.ksrpc.channels.SerializedService
import com.monkopedia.ksrpc.channels.SuspendInit
import com.monkopedia.ksrpc.channels.asJsonRpcConnection
import com.monkopedia.ksrpc.internal.ThreadSafeManager.threadSafe
import com.monkopedia.ksrpc.internal.appendLine
import com.monkopedia.ksrpc.internal.jsonrpc.JsonRpcChannel
import com.monkopedia.ksrpc.internal.jsonrpc.JsonRpcRequest
import com.monkopedia.ksrpc.internal.jsonrpc.JsonRpcResponse
import com.monkopedia.ksrpc.internal.jsonrpc.JsonRpcSerializedChannel
import com.monkopedia.ksrpc.internal.jsonrpc.JsonRpcServiceWrapper
import com.monkopedia.ksrpc.internal.jsonrpc.JsonRpcWriterBase
import com.monkopedia.ksrpc.internal.jsonrpc.jsonHeader
import com.monkopedia.ksrpc.internal.jsonrpc.jsonLine
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.close
import io.ktor.utils.io.readUTF8Line
import kotlin.coroutines.coroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement

class JsonRpcTest {

    @Test
    fun testLineSender_send() = runBlockingUnit {
        val expectedMessage = """{"jsonrpc":"2.0","method":"subtract","params":[42,23],"id":1}"""
        val outputChannel = ByteChannel()
        val inputChannel = ByteChannel()
        val sender = (inputChannel to outputChannel).jsonLine(ksrpcEnvironment { })
        sender.send(
            Json.encodeToJsonElement(
                JsonRpcRequest(
                    method = "subtract",
                    params = Json.decodeFromString("[42,23]"),
                    id = JsonPrimitive(1)
                )
            )
        )
        assertEquals(expectedMessage, outputChannel.readUTF8Line())
    }

    @Test
    fun testLineReceiver_send() = runBlockingUnit {
        val expectedResponse = """{"jsonrpc":"2.0","result":-19,"id":1}"""
        val outputChannel = ByteChannel()
        val inputChannel = ByteChannel()
        val sender = (inputChannel to outputChannel).jsonLine(ksrpcEnvironment { })
        sender.send(
            Json.encodeToJsonElement(
                JsonRpcResponse(result = Json.decodeFromString("-19"), id = JsonPrimitive(1))
            )
        )
        assertEquals(expectedResponse, outputChannel.readUTF8Line())
    }

    @Test
    fun testLineReceiver_receive() = runBlockingUnit {
        val expectedMessage = """{"jsonrpc":"2.0","method":"subtract","params":[42,23],"id":1}"""
        val outputChannel = ByteChannel()
        val inputChannel = ByteChannel()
        inputChannel.appendLine(expectedMessage)
        inputChannel.flush()
        val sender = (inputChannel to outputChannel).jsonLine(ksrpcEnvironment { })
        val expectedRequest =
            Json.encodeToJsonElement(
                JsonRpcRequest(
                    method = "subtract",
                    params = Json.decodeFromString("[42,23]"),
                    id = JsonPrimitive(1)
                )
            )
        assertEquals(expectedRequest, sender.receive())
    }

    @Test
    fun testLineSender_receive() = runBlockingUnit {
        val expectedResponse = """{"jsonrpc":"2.0","result":-19,"id":1}"""
        val outputChannel = ByteChannel()
        val inputChannel = ByteChannel()
        inputChannel.appendLine(expectedResponse)
        inputChannel.flush()
        val sender = (inputChannel to outputChannel).jsonLine(ksrpcEnvironment { })
        val expectedRequest =
            Json.encodeToJsonElement(
                JsonRpcResponse(result = Json.decodeFromString("-19"), id = JsonPrimitive(1))
            )
        assertEquals(expectedRequest, sender.receive())
    }

    @Test
    fun testHeaderSender_send() = runBlockingUnit {
        val expectedMessage = """{"jsonrpc":"2.0","method":"subtract","params":[42,23],"id":1}"""
        val outputChannel = ByteChannel()
        val inputChannel = ByteChannel()
        val sender = (inputChannel to outputChannel).jsonHeader(ksrpcEnvironment { })
        sender.send(
            Json.encodeToJsonElement(
                JsonRpcRequest(
                    method = "subtract",
                    params = Json.decodeFromString("[42,23]"),
                    id = JsonPrimitive(1)
                )
            )
        )
        assertEquals("Content-Length: 61", outputChannel.readUTF8Line())
        assertEquals("Content-Type: $DEFAULT_CONTENT_TYPE", outputChannel.readUTF8Line())
        assertEquals("", outputChannel.readUTF8Line())
        assertEquals(
            expectedMessage,
            ByteArray(61).apply { outputChannel.readFully(this, 0, 61) }.decodeToString()
        )
    }

    @Test
    fun testHeaderReceiver_send() = runBlockingUnit {
        val expectedResponse = """{"jsonrpc":"2.0","result":-19,"id":1}"""
        val outputChannel = ByteChannel()
        val inputChannel = ByteChannel()
        val sender = (inputChannel to outputChannel).jsonHeader(ksrpcEnvironment { })
        sender.send(
            Json.encodeToJsonElement(
                JsonRpcResponse(result = Json.decodeFromString("-19"), id = JsonPrimitive(1))
            )
        )
        assertEquals("Content-Length: 37", outputChannel.readUTF8Line())
        assertEquals("Content-Type: $DEFAULT_CONTENT_TYPE", outputChannel.readUTF8Line())
        assertEquals("", outputChannel.readUTF8Line())
        assertEquals(
            expectedResponse,
            ByteArray(37).apply { outputChannel.readFully(this, 0, 37) }.decodeToString()
        )
    }

    @Test
    fun testHeaderReceiver_receive() = runBlockingUnit {
        val expectedMessage = """{"jsonrpc":"2.0","method":"subtract","params":[42,23],"id":1}"""
        val outputChannel = ByteChannel()
        val inputChannel = ByteChannel()
        inputChannel.appendLine("Content-Length: 61")
        inputChannel.appendLine("Content-Type: $DEFAULT_CONTENT_TYPE")
        inputChannel.appendLine()
        inputChannel.appendLine(expectedMessage)
        inputChannel.flush()
        val sender = (inputChannel to outputChannel).jsonHeader(ksrpcEnvironment { })
        val expectedRequest =
            Json.encodeToJsonElement(
                JsonRpcRequest(
                    method = "subtract",
                    params = Json.decodeFromString("[42,23]"),
                    id = JsonPrimitive(1)
                )
            )
        assertEquals(expectedRequest, sender.receive())
    }

    @Test
    fun testHeaderSender_receive() = runBlockingUnit {
        val expectedResponse = """{"jsonrpc":"2.0","result":-19,"id":1}"""
        val outputChannel = ByteChannel()
        val inputChannel = ByteChannel()
        inputChannel.appendLine("Content-Length: 37")
        inputChannel.appendLine("Content-Type: $DEFAULT_CONTENT_TYPE")
        inputChannel.appendLine()
        inputChannel.appendLine(expectedResponse)
        inputChannel.flush()
        val sender = (inputChannel to outputChannel).jsonHeader(ksrpcEnvironment { })
        val expectedRequest =
            Json.encodeToJsonElement(
                JsonRpcResponse(result = Json.decodeFromString("-19"), id = JsonPrimitive(1))
            )
        assertEquals(expectedRequest, sender.receive())
    }

    @Test
    fun testSerializedChannel() = runBlockingUnit {
        val params = "[42, 23]"
        val fakeResponse = """{"jsonrpc": "2.0", "result": -19, "id": 1}"""
        val (outOut, outIn) = createPipe()
        val (inOut, inIn) = createPipe()
        GlobalScope.launch(Dispatchers.Default) {
            outIn.readUTF8Line()
            inOut.appendLine(fakeResponse)
            inOut.flush()
        }
        val expectedMessage = """{"jsonrpc":"2.0","method":"subtract","params":[42,23],"id":1}"""
        val expectedResponse = """-19"""
        val jsonChannel = threadSafe<JsonRpcChannel> { context ->
            JsonRpcWriterBase(
                CoroutineScope(context),
                context,
                ksrpcEnvironment { },
                (inIn to outOut).jsonLine(ksrpcEnvironment { })
            )
        }.also {
            (it as? SuspendInit)?.init()
        }
        assertEquals(
            expectedResponse,
            Json.encodeToString(
                jsonChannel.execute(
                    "subtract",
                    Json.decodeFromString(params),
                    false
                )
            )
        )
        jsonChannel.close()
        try {
            inOut.close()
        } catch (t: Throwable) {
            // Don't care
        }
        try {
            outOut.close()
        } catch (t: Throwable) {
            // Don't care
        }
    }
}

abstract class JsonRpcFunctionalityTest(
    private val serializedChannel: suspend () -> SerializedService,
    private val verifyOnChannel: suspend (SerializedService) -> Unit
) {

    @Test
    fun testJsonRpcChannel() = runBlockingUnit {
        val serializedChannel = serializedChannel()
        val channel = JsonRpcServiceWrapper(serializedChannel)

        verifyOnChannel(
            JsonRpcSerializedChannel(
                coroutineContext,
                channel,
                ksrpcEnvironment { }
            )
        )
    }

    @Test
    fun testPipePassthrough() = runBlockingUnit {
        val (output, input) = createPipe()
        val (so, si) = createPipe()
        GlobalScope.launch(Dispatchers.Default) {
            val serializedChannel = serializedChannel()
            val connection = (input to so).asJsonRpcConnection(
                ksrpcEnvironment {
                    errorListener = ErrorListener {
                        it.printStackTrace()
                    }
                }
            )
            connection.registerDefault(serializedChannel)
        }
        try {
            val channel = (si to output).asJsonRpcConnection(
                ksrpcEnvironment {
                    errorListener = ErrorListener {
                        it.printStackTrace()
                    }
                },
                true
            )
            verifyOnChannel(channel.defaultChannel())
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
        }
    }

    @Test
    fun testPipePassthroughWithLines() = runBlockingUnit {
        val (output, input) = createPipe()
        val (so, si) = createPipe()
        GlobalScope.launch(Dispatchers.Default) {
            val serializedChannel = serializedChannel()
            val connection = (input to so).asJsonRpcConnection(
                ksrpcEnvironment {
                    errorListener = ErrorListener {
                        it.printStackTrace()
                    }
                },
                false
            )
            connection.registerDefault(serializedChannel)
        }
        try {
            val channel = (si to output).asJsonRpcConnection(
                ksrpcEnvironment {
                    errorListener = ErrorListener {
                        it.printStackTrace()
                    }
                },
                false
            )
            verifyOnChannel(channel.defaultChannel())
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
        }
    }
}

object JsonRpcTypeTest {

    //abstract class JsonRpcTypeFunctionalityTest(
        //verifyOnChannel: suspend (SerializedService, FakeTestTypes) -> Unit,
        //private val service: FakeTestTypes = FakeTestTypes()
    //) : JsonRpcFunctionalityTest(
        //serializedChannel = { service.serialized<TestTypesInterface>(ksrpcEnvironment { }) },
        //verifyOnChannel = { channel ->
            //verifyOnChannel(channel, service)
        //}
    //)

    //class PairStrTest : JsonRpcTypeFunctionalityTest(
        //verifyOnChannel = { channel, service ->
            //val stub = channel.toStub<TestTypesInterface>()
            //service.nextReturn.value = ""
            //stub.rpc("Hello" to "world")
            //assertEquals("rpc", service.lastCall.value)
            //assertEquals("Hello" to "world", service.lastInput.value)
        //}
    //)

    //class MapTest : JsonRpcTypeFunctionalityTest(
        //verifyOnChannel = { channel, service ->
            //val stub = channel.toStub<TestTypesInterface>()
            //val completion = CompletableDeferred<Unit>()
            //service.callComplete.value = completion
            //service.nextReturn.value = Unit
            //stub.mapRpc(
                //mutableMapOf(
                    //"First" to MyJson("first", 1, null),
                    //"Second" to MyJson("second", 2, 1.2f),
                //)
            //)
            //completion.await()
            //assertEquals("mapRpc", service.lastCall.value)
            //assertEquals(
                //mutableMapOf(
                    //"First" to MyJson("first", 1, null),
                    //"Second" to MyJson("second", 2, 1.2f),
                //),
                //service.lastInput.value
            //)
        //}
    //)

    //class InputIntTest : JsonRpcTypeFunctionalityTest(
        //verifyOnChannel = { channel, service ->
            //val stub = channel.toStub<TestTypesInterface>()
            //val completion = CompletableDeferred<Unit>()
            //service.callComplete.value = completion
            //service.nextReturn.value = Unit
            //stub.inputInt(42)
            //completion.await()
            //assertEquals("inputInt", service.lastCall.value)
            //assertEquals(42, service.lastInput.value)
        //}
    //)

    //class InputIntListTest : JsonRpcTypeFunctionalityTest(
        //verifyOnChannel = { channel, service ->
            //val stub = channel.toStub<TestTypesInterface>()
            //val completion = CompletableDeferred<Unit>()
            //service.callComplete.value = completion
            //service.nextReturn.value = Unit
            //stub.inputIntList(listOf(42))
            //completion.await()
            //assertEquals("inputIntList", service.lastCall.value)
            //assertEquals(listOf(42), service.lastInput.value)
        //}
    //)

    //class InputIntNullableTest : JsonRpcTypeFunctionalityTest(
        //verifyOnChannel = { channel, service ->
            //val stub = channel.toStub<TestTypesInterface>()
            //val completion = CompletableDeferred<Unit>()
            //service.callComplete.value = completion
            //service.nextReturn.value = Unit
            //stub.inputIntNullable(null)
            //completion.await()
            //assertEquals("inputIntNullable", service.lastCall.value)
            //assertEquals(null, service.lastInput.value)
        //}
    //)

    //class OutputIntTest : JsonRpcTypeFunctionalityTest(
        //verifyOnChannel = { channel, service ->
            //val stub = channel.toStub<TestTypesInterface>()
            //service.nextReturn.value = 42
            //assertEquals(42, stub.outputInt(Unit))
            //assertEquals("outputInt", service.lastCall.value)
            //assertEquals(Unit, service.lastInput.value)
        //}
    //)

    //class OutputIntNullableTest : JsonRpcTypeFunctionalityTest(
        //verifyOnChannel = { channel, service ->
            //val stub = channel.toStub<TestTypesInterface>()
            //service.nextReturn.value = null
            //assertEquals(null, stub.outputIntNullable(Unit))
            //assertEquals("outputIntNullable", service.lastCall.value)
            //assertEquals(Unit, service.lastInput.value)
        //}
    //)

    //class ReturnTypeTest : JsonRpcTypeFunctionalityTest(
        //verifyOnChannel = { channel, service ->
            //val stub = channel.toStub<TestTypesInterface>()
            //service.nextReturn.value = MyJson("second", 2, 1.2f)
            //assertEquals(MyJson("second", 2, 1.2f), stub.returnType(Unit))
            //assertEquals("returnType", service.lastCall.value)
            //assertEquals(Unit, service.lastInput.value)
        //}
    //)
}
