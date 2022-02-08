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
import com.monkopedia.ksrpc.serve as jvmServe
import io.ktor.application.install
import io.ktor.routing.Routing
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.websocket.WebSockets
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking

var PORT = 8081

actual typealias Routing = Routing

actual suspend inline fun httpTest(
    crossinline serve: suspend Routing.() -> Unit,
    test: suspend (Int) -> Unit
) {
    val port = PORT++
    val serverCompletion = CompletableDeferred<ApplicationEngine>()
    GlobalScope.launch(Dispatchers.IO) {
        try {
            serverCompletion.complete(
                embeddedServer(Netty, port) {
                    install(WebSockets)
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

actual suspend fun testServe(
    basePath: String,
    channel: SerializedService,
    env: KsrpcEnvironment
) = jvmServe(basePath, channel, env)

actual fun Routing.testServeWebsocket(
    basePath: String,
    channel: SerializedService,
    env: KsrpcEnvironment
) = serveWebsocket(basePath, channel, env)

internal actual fun runBlockingUnit(function: suspend () -> Unit) {
    val block = CountDownLatch(1)
    val executor = newSingleThreadContext("Test thread")
    var exc: Throwable? = null
    GlobalScope.launch(executor) {
        try {
            function()
        } catch (t: Throwable) {
            exc = t
        } finally {
            block.countDown()
        }
    }
    block.await()
    exc?.let { throw it }
}
