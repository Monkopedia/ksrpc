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
import io.ktor.util.toByteArray
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readRemaining
import kotlin.test.assertEquals

@KsService
interface BinaryInterface : RpcService {
    @KsMethod("/rpc")
    suspend fun rpc(u: Pair<String, String>): ByteReadChannel

    @KsMethod("/input")
    suspend fun inputRpc(u: ByteReadChannel): String
}

class BinaryTest : RpcFunctionalityTest(
    serializedChannel = {
        val channel: BinaryInterface = object : BinaryInterface {
            override suspend fun rpc(u: Pair<String, String>): ByteReadChannel {
                val str = "${u.first} ${u.second}"
                return ByteReadChannel(str.encodeToByteArray())
            }

            override suspend fun inputRpc(u: ByteReadChannel): String {
                error("Not implemented")
            }
        }
        channel.serialized(ksrpcEnvironment {  })
    },
    verifyOnChannel = { serializedChannel ->
        val stub = serializedChannel.toStub<BinaryInterface>()
        val response = stub.rpc("Hello" to "world")
        val str = response.readRemaining().readText()
        assertEquals("Hello world", str)
    }
)

class BinaryInputTest : RpcFunctionalityTest(
    serializedChannel = {
        val channel: BinaryInterface = object : BinaryInterface {
            override suspend fun inputRpc(u: ByteReadChannel): String {
                return "Input: " + u.toByteArray().decodeToString()
            }

            override suspend fun rpc(u: Pair<String, String>): ByteReadChannel {
                error("Not implemented")
            }
        }
        channel.serialized(ksrpcEnvironment {  })
    },
    verifyOnChannel = { serializedChannel ->
        val stub = serializedChannel.toStub<BinaryInterface>()
        val response = stub.inputRpc(ByteReadChannel("Hello world".encodeToByteArray()))
        assertEquals("Input: Hello world", response)
    }
)
