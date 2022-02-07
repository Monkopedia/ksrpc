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

import internal.MovableInstance
import internal.using
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlin.test.fail
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import platform.posix.pthread_self
import kotlin.native.concurrent.withWorker
import kotlin.test.Test
import kotlin.test.assertEquals

actual suspend inline fun httpTest(
    crossinline serve: suspend Routing.() -> Unit,
    test: suspend (Int) -> Unit
) {
    // Do nothing, disable HTTP hosting in Native tests.
}

actual suspend fun testServe(
    basePath: String,
    channel: SerializedService,
    env: KsrpcEnvironment
): Routing.() -> Unit = {
    // Do nothing, disable HTTP hosting in Native tests.
}

actual fun Routing.testServeWebsocket(
    basePath: String,
    channel: SerializedService,
    env: KsrpcEnvironment
) {
    // Do nothing, disable HTTP hosting in Native tests.
}

actual class Routing

internal actual fun runBlockingUnit(function: suspend () -> Unit) {
    try {
        runBlocking {
            function()
        }
    } catch (t: Throwable) {
        t.printStackTrace()
        fail("Caught exception $t")
    }
}

class NativeTestTest {
    @Test
    fun testThreads() = runBlockingUnit {
        val specialThread = newSingleThreadContext("Single thread")
        println("Thread: ${pthread_self()}")
        withContext(specialThread) {
            println("Other Thread: ${pthread_self()}")
        }
        println("Thread: ${pthread_self()}")
        withContext(specialThread) {
            println("Other Thread: ${pthread_self()}")
        }
        val x = withContext(specialThread) {
            (5 + 10).also {
                println("Other Thread: $it")
            }
        }
        println("Thread: $x")
    }

    @Test
    fun testMovableInstance() = runBlockingUnit {
        val movable = MovableInstance { mutableSetOf<String>() }
        val otherThread = newSingleThreadContext("test thread")

        try {
            movable.using {
                it.add("First string")
            }
            GlobalScope.launch(otherThread) {
                movable.using {
                    it.add("Second string")
                }
            }.join()
            movable.using {
                assertEquals(it, setOf("First string", "Second string"))
            }
        } finally {
            otherThread.close()
        }
    }
}