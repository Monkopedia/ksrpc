package com.monkopedia.ksrpc

import io.ktor.util.*
import io.ktor.utils.io.*
import kotlin.test.assertEquals

class TestJniImpl : JniTestInterface {
    override suspend fun binaryRpc(u: Pair<String, String>): ByteReadChannel {
        if (u.first == "Long") {
            return ByteReadChannel(jniTestContent.encodeToByteArray())
        }
        val str = "${u.first} ${u.second}"
        return ByteReadChannel(str.encodeToByteArray())
    }

    override suspend fun inputRpc(u: ByteReadChannel): String {
        return "Input: " + u.toByteArray().decodeToString()
    }

    override suspend fun ping(input: String): String {
        assertEquals("ping", input)
        return "pong"
    }

    override suspend fun rpc(u: Pair<String, String>): String {
        return "${u.first} ${u.second}"
    }

    override suspend fun subservice(prefix: String): TestSubInterface {
        return object : TestSubInterface {
            override suspend fun rpc(u: Pair<String, String>): String {
                return "$prefix ${u.first} ${u.second}"
            }
        }
    }
}
