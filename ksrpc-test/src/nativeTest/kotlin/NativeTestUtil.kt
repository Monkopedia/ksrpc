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
@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.ksrpc

import com.monkopedia.ksrpc.channels.Connection
import com.monkopedia.ksrpc.channels.SerializedService
import com.monkopedia.ksrpc.ktor.serveHttp as nativeServe
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
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import platform.posix.pipe
import platform.posix.pthread_self

@PublishedApi
internal val nextPort = atomic(9181)

@PublishedApi
internal const val MAX_BIND_ATTEMPTS = 32
val serverDispatcher = newFixedThreadPoolContext(8, "server-threads")

internal actual fun platformSupportedTestTypes(): Set<RpcFunctionalityTest.TestType> = setOf(
    RpcFunctionalityTest.TestType.SERIALIZE,
    RpcFunctionalityTest.TestType.PIPE,
    RpcFunctionalityTest.TestType.HTTP
)

actual suspend inline fun httpTest(
    crossinline serve: suspend Routing.() -> Unit,
    test: suspend (Int) -> Unit,
    isWebsocket: Boolean
) {
    if (isWebsocket) return
    repeat(MAX_BIND_ATTEMPTS) {
        val port = nextPort.getAndIncrement()
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
        val serverResult = runCatching { serverCompletion.await() }
        if (serverResult.isFailure) {
            val failure = serverResult.exceptionOrNull()
            if (failure != null && failure.isAddressInUse()) {
                return@repeat
            }
            throw serverResult.exceptionOrNull()!!
        }
        val server = serverResult.getOrThrow()
        try {
            test(port)
        } finally {
            server.stop(1_000, 3_000)
        }
        return
    }
    error("Unable to bind test server after $MAX_BIND_ATTEMPTS attempts")
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

internal actual suspend fun serviceWorkerTest(
    serviceName: String?,
    test: suspend (Connection<String>) -> Unit
) {
    // Service workers are not supported on native.
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

actual typealias Routing = io.ktor.server.routing.Routing

actual typealias RunBlockingReturn = Unit
internal actual fun runBlockingUnit(function: suspend CoroutineScope.() -> Unit) {
    var failure: Throwable? = null
    runBlocking {
        // Invoke the test body directly in the runBlocking coroutine so this returns the
        // instant the body's own code completes, then cancel any coroutines the body left
        // running. Tests routinely build connections (e.g. JsonRpcWriterBase /
        // ReadWritePacketChannel) whose receive loop is launched in a scope parented to this
        // runBlocking coroutine; if a test abandons such a connection without closing it
        // (e.g. JsonRpcTierCheckTest, which throws on a tier mismatch before any close), that
        // receive loop never terminates. Plain `runBlocking { body() }` would then block
        // forever after the body returns, waiting for that orphaned child — the native suite
        // hang in #214. The JVM/JS test harnesses return as soon as the body completes and
        // let such orphans be reaped at process exit; mirror that here by cancelling whatever
        // background coroutines remain once the body has finished.
        try {
            function()
        } catch (t: Throwable) {
            failure = t
        } finally {
            // Cancel any leftover children (orphaned receive loops, server jobs, etc.) so
            // runBlocking can return instead of awaiting coroutines the test forgot to close.
            coroutineContext.job.cancelChildren()
        }
    }
    failure?.let {
        it.printStackTrace()
        fail("Caught exception $it")
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

@PublishedApi
internal fun Throwable.isAddressInUse(): Boolean = generateSequence(this) { it.cause }.any {
    val message = it.message ?: return@any false
    message.contains("Address already in use", ignoreCase = true) ||
        message.contains("EADDRINUSE", ignoreCase = true)
}
