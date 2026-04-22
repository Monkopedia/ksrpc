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
import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlinx.io.readByteArray
import kotlinx.io.readString

@KsService
interface KxioBinaryInterface : RpcService {
    @KsMethod("/rpc")
    suspend fun rpc(u: Pair<String, String>): Source

    @KsMethod("/input")
    suspend fun inputRpc(u: Source): String

    @KsMethod("/ping")
    suspend fun ping(input: String): String
}

private fun sourceOf(bytes: ByteArray): Source = Buffer().apply { write(bytes, 0, bytes.size) }

class SourceBinaryTest :
    RpcFunctionalityTest(
        serializedChannel = {
            val channel: KxioBinaryInterface = object : KxioBinaryInterface {
                override suspend fun rpc(u: Pair<String, String>): Source {
                    val str = "${u.first} ${u.second}"
                    return sourceOf(str.encodeToByteArray())
                }

                override suspend fun inputRpc(u: Source): String {
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
            val stub = serializedChannel.toStub<KxioBinaryInterface, String>()
            val response = stub.rpc("Hello" to "world")
            assertEquals("Hello world", response.readString())
            assertEquals("pong", stub.ping("ping"))
        }
    )

class SourceBinaryInputTest :
    RpcFunctionalityTest(
        serializedChannel = {
            val channel: KxioBinaryInterface = object : KxioBinaryInterface {
                override suspend fun inputRpc(u: Source): String =
                    "Input: " + u.readByteArray().decodeToString()

                override suspend fun rpc(u: Pair<String, String>): Source {
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
            val stub = serializedChannel.toStub<KxioBinaryInterface, String>()
            val response = stub.inputRpc(sourceOf("Hello world".encodeToByteArray()))
            assertEquals("Input: Hello world", response)
            assertEquals("pong", stub.ping("ping"))
        }
    )
