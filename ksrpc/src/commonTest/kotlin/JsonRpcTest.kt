package com.monkopedia.ksrpc

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class JsonRpcTest {

    @Test
    fun testSerializedChannel() = runBlockingUnit {
        val params = "[42, 23]"
        var lastMessage: String? = null
        val fakeResponse = """{"jsonrpc": "2.0", "result": -19, "id": 1}"""
        val fakeJsonRpcChannel = object : JsonRpcChannel {
            override suspend fun execute(message: String): String? {
                lastMessage = message
                return fakeResponse
            }
        }
        val expectedMessage = """{"jsonrpc":"2.0","method":"subtract","params":[42,23],"id":1}"""
        val expectedResponse = """-19"""
        assertEquals(expectedResponse, JsonRpcSerializedChannel(fakeJsonRpcChannel, Json).call("subtract", CallData.create(params)).readSerialized())
        assertEquals(expectedMessage, lastMessage)
    }

    @Test
    fun testSerializedChannel_notify() = runBlockingUnit {
        val params = "[42, 23]"
        var lastMessage: String? = null
        val fakeResponse = null
        val fakeJsonRpcChannel = object : JsonRpcChannel {
            override suspend fun execute(message: String): String? {
                lastMessage = message
                return fakeResponse
            }
        }
        val expectedMessage = """{"jsonrpc":"2.0","method":"subtract","params":[42,23]}"""
        val expectedResponse = null
        assertEquals("{}", JsonRpcSerializedChannel(fakeJsonRpcChannel, Json).call("subtract", CallData.create(params)).readSerialized())
        assertEquals(expectedMessage, lastMessage)
    }
}
