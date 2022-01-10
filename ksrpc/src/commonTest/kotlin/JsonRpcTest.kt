package com.monkopedia.ksrpc

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class JsonRpcTest {

    @Test
    fun testSerializedChannel() = runBlockingUnit {
        val params = "[42, 23]"
        var lastMessage: String? = null
        val fakeJsonRpcChannel = object : JsonRpcChannel {
            override suspend fun execute(message: String): String? {
                lastMessage = message
                return "-19"
            }
        }
        val expectedMessage = """{"jsonrpc": "2.0", "method": "subtract", "params": [42, 23], "id": 1}"""
        val expectedResponse = """{"jsonrpc": "2.0", "result": -19, "id": 1}"""
        assertEquals(expectedResponse, JsonRpcSerializedChannel(fakeJsonRpcChannel, Json).call("subtract", CallData.create(params)).readSerialized())
        assertEquals(expectedMessage, lastMessage)
    }
}