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
@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.ksrpc

import com.monkopedia.ksrpc.channels.SerializedService
import com.monkopedia.ksrpc.ktor.serve as nativeServe
import com.monkopedia.ksrpc.sockets.posixFileReadChannel
import com.monkopedia.ksrpc.sockets.posixFileWriteChannel
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import kotlin.test.Test
import kotlin.test.fail
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import platform.posix.pipe
import platform.posix.pthread_self

var PORT = 9181
val serverDispatcher = newFixedThreadPoolContext(8, "server-threads")

actual suspend inline fun httpTest(
    crossinline serve: suspend Routing.() -> Unit,
    test: suspend (Int) -> Unit,
    isWebsocket: Boolean
) {
    if (isWebsocket) return
    val port = PORT++
    val serverCompletion = CompletableDeferred<EmbeddedServer<*, *>>()
    GlobalScope.launch(serverDispatcher) {
        try {
            serverCompletion.complete(
                embeddedServer(CIO, port) {
                    routing {
                        runBlocking {
                            serve()
                        }
                    }
                }.start()
            )
        } catch (t: Throwable) {
            serverCompletion.completeExceptionally(t)
        }
    }
    val server = serverCompletion.await()
    try {
        test(port)
    } finally {
        server.stop(500, 500)
    }
}

actual suspend fun Routing.testServe(
    basePath: String,
    channel: SerializedService<String>,
    env: KsrpcEnvironment<String>
): Unit = nativeServe(basePath, channel, env)

actual fun Routing.testServeWebsocket(
    basePath: String,
    channel: SerializedService<String>,
    env: KsrpcEnvironment<String>
) = Unit

actual fun createPipe(): Pair<ByteWriteChannel, ByteReadChannel> {
    memScoped {
        val pipe = allocArray<IntVar>(2)
        require(pipe(pipe) >= 0) {
            "Failed to create pipe"
        }
        return posixFileWriteChannel(pipe[1]) to posixFileReadChannel(pipe[0])
    }
}

actual typealias Routing = io.ktor.server.routing.Routing

actual typealias RunBlockingReturn = Unit
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
