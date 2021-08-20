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

import kotlin.test.assertEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async

abstract class OverlappingTest() : OverlappingTestBase()

// Hacks for firstCall usage :/.
abstract class OverlappingTestBase(
    var firstCall: CompletableDeferred<String> = CompletableDeferred<String>()
) : RpcFunctionalityTest(
    serializedChannel = {
        // Waits for the second call to come in before the first finishes.
        val service: TestInterface = object : TestInterface {
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
        service.serialized()
    },
    verifyOnChannel = { serializedChannel ->
        val stub = serializedChannel.toStub<TestInterface>()

        val finish = GlobalScope.async(Dispatchers.IO) {
            assertEquals("Hello second", stub.rpc("Hello" to "first"))
        }
        assertEquals("first", firstCall.await())
        assertEquals("Hello second", stub.rpc("Hello" to "second"))
        finish.await()
    }
)
