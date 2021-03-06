/*
 * Copyright 2020 Jason Monk
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

import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import kotlin.test.assertEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.junit.Test

class OverlappingTest {
    internal val info = TestInterface.info

    @Test
    fun testMultiCallOverlap() = runBlockingUnit {
        var firstCall = CompletableDeferred<String>()
        // Waits for the second call to come in before the first finishes.
        val service = object : Service(), TestInterface {
            var secondCall: CompletableDeferred<String>? = null

            override suspend fun rpc(u: Pair<String, String>): String {
                if (secondCall == null) {
                    firstCall.complete(u.second)
                    secondCall = CompletableDeferred()
                    return "${u.first} ${secondCall?.await()}"
                } else {
                    secondCall?.complete(u.second)
                    return "${u.first} ${u.second}"
                }
            }
        }
        val channel = info.createChannelFor(service)
        val client = channel.servePipe()
        val stub = TestInterface.wrap(client.asChannel().deserialized())

        val finish = GlobalScope.async(Dispatchers.IO) {
            assertEquals("Hello second", stub.rpc("Hello" to "first"))
        }
        assertEquals("first", firstCall.await())
        assertEquals("Hello second", stub.rpc("Hello" to "second"))
        finish.await()
    }

    fun RpcChannel.servePipe(): Pair<InputStream, OutputStream> {
        val serializedChannel = serialized(TestTypesInterface)
        val (output, input) = createPipe()
        val (so, si) = createPipe()
        GlobalScope.launch(Dispatchers.IO) {
            serializedChannel.serve(
                si, output,
                errorListener = {
                    it.printStackTrace()
                }
            )
        }
        return input to so
    }

    private fun createPipe(): Pair<OutputStream, InputStream> {
        return PipedInputStream().let { PipedOutputStream(it) to it }
    }
}
