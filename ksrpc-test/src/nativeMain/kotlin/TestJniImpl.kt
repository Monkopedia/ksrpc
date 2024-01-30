/**
 * Copyright (C) 2024 Jason Monk <monkopedia@gmail.com>
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

import io.ktor.util.toByteArray
import io.ktor.utils.io.ByteReadChannel
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

    override suspend fun subservice(prefix: String): JniTestSubInterface {
        return object : JniTestSubInterface {
            override suspend fun rpc(u: Pair<String, String>): String {
                return "$prefix ${u.first} ${u.second}"
            }
        }
    }
}
