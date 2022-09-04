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

import com.monkopedia.ksrpc.channels.SerializedService
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import kotlin.test.Test
import kotlin.test.fail
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import platform.posix.pipe
import platform.posix.pthread_self

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

actual fun createPipe(): Pair<ByteWriteChannel, ByteReadChannel> {
    memScoped {
        val pipe = allocArray<IntVar>(2)
        require(pipe(pipe) >= 0) {
            "Failed to create pipe"
        }
        return posixFileWriteChannel(pipe[1]) to posixFileReadChannel(pipe[0])
    }
}

actual class Routing

internal actual fun runBlockingUnit(function: suspend CoroutineScope.() -> Unit) {
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
}
