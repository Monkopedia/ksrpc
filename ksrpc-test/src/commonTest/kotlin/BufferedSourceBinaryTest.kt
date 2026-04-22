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
import kotlin.test.assertEquals
import okio.Buffer
import okio.BufferedSource

@KsService
interface OkioBinaryInterface : RpcService {
    @KsMethod("/rpc")
    suspend fun rpc(u: Pair<String, String>): BufferedSource

    @KsMethod("/input")
    suspend fun inputRpc(u: BufferedSource): String

    @KsMethod("/ping")
    suspend fun ping(input: String): String
}

private fun bufferedSourceOf(bytes: ByteArray): BufferedSource =
    Buffer().apply { write(bytes, 0, bytes.size) }

class BufferedSourceBinaryTest :
    RpcFunctionalityTest(
        serializedChannel = {
            val channel: OkioBinaryInterface = object : OkioBinaryInterface {
                override suspend fun rpc(u: Pair<String, String>): BufferedSource {
                    val str = "${u.first} ${u.second}"
                    return bufferedSourceOf(str.encodeToByteArray())
                }

                override suspend fun inputRpc(u: BufferedSource): String {
                    error("Not implemented")
                }

                override suspend fun ping(input: String): String {
                    assertEquals("ping", input)
                    return "pong"
                }
            }
            channel.serialized(ksrpcEnvironment { })
        },
        verifyOnChannel = { serializedChannel ->
            val stub = serializedChannel.toStub<OkioBinaryInterface, String>()
            val response = stub.rpc("Hello" to "world")
            assertEquals("Hello world", response.readUtf8())
            assertEquals("pong", stub.ping("ping"))
        }
    )

class BufferedSourceBinaryInputTest :
    RpcFunctionalityTest(
        serializedChannel = {
            val channel: OkioBinaryInterface = object : OkioBinaryInterface {
                override suspend fun inputRpc(u: BufferedSource): String =
                    "Input: " + u.readByteArray().decodeToString()

                override suspend fun rpc(u: Pair<String, String>): BufferedSource {
                    error("Not implemented")
                }

                override suspend fun ping(input: String): String {
                    assertEquals("ping", input)
                    return "pong"
                }
            }
            channel.serialized(ksrpcEnvironment { })
        },
        verifyOnChannel = { serializedChannel ->
            val stub = serializedChannel.toStub<OkioBinaryInterface, String>()
            val response = stub.inputRpc(bufferedSourceOf("Hello world".encodeToByteArray()))
            assertEquals("Input: Hello world", response)
            assertEquals("pong", stub.ping("ping"))
        }
    )
