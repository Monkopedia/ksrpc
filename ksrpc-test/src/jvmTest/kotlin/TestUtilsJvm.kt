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
package com.monkopedia.ksrpc

import com.monkopedia.ksrpc.channels.SerializedService
import com.monkopedia.ksrpc.ktor.serve as jvmServe
import com.monkopedia.ksrpc.ktor.websocket.serveWebsocket
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

var PORT = 8081

actual typealias Routing = io.ktor.server.routing.Routing

actual suspend inline fun httpTest(
    crossinline serve: suspend Routing.() -> Unit,
    test: suspend (Int) -> Unit,
    isWebsocket: Boolean
) {
    val port = PORT++
    val serverCompletion = CompletableDeferred<EmbeddedServer<*, *>>()
    GlobalScope.launch(Dispatchers.IO) {
        try {
            serverCompletion.complete(
                embeddedServer(Netty, port) {
                    install(WebSockets) {
                        contentConverter = KotlinxWebsocketSerializationConverter(Json)
                    }
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
): Unit = jvmServe(basePath, channel, env)

actual fun Routing.testServeWebsocket(
    basePath: String,
    channel: SerializedService<String>,
    env: KsrpcEnvironment<String>
) = serveWebsocket(basePath, channel, env)

actual fun createPipe(): Pair<ByteWriteChannel, ByteReadChannel> {
    val channel = ByteChannel(autoFlush = true)
    return channel to channel
}

actual typealias RunBlockingReturn = Unit

@OptIn(DelicateCoroutinesApi::class)
internal actual fun runBlockingUnit(function: suspend CoroutineScope.() -> Unit) {
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
