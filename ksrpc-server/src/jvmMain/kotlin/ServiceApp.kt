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
package com.monkopedia.ksrpc.server

import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.monkopedia.ksrpc.ktor.websocket.serveWebsocket
import com.monkopedia.ksrpc.sockets.asConnection
import io.ktor.server.application.Application
import io.ktor.server.engine.BaseApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.routing.Routing
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlin.system.exitProcess
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withContext

/**
 * Base class that makes it easy to host a default service on any combination of
 * std in/out, http, and sockets.
 */
@OptIn(DelicateCoroutinesApi::class)
actual abstract class ServiceApp actual constructor(appName: String) : BaseServiceApp(appName) {

    val port by option(
        "-p",
        "--port",
        help = "Host this service as ksrpc service on specified port"
    ).int().multiple()
    val enableWebsockets by option(
        "-w",
        "--websockets",
        help = "Enable websockets on any http ports"
    ).flag()
    val noHttp by option(
        "--no-http",
        help = "Disable http requests to allow for a web-socket only service."
    ).flag()

    override fun run() {
        if (!stdOut && port.isEmpty() && http.isEmpty()) {
            println("No output mechanism specified, exiting")
            exitProcess(1)
        }
        for (p in port) {
            env.logger.info("ServiceApp", "Hosting socket on $p")
            thread(start = true) {
                val socket = ServerSocket(p)
                while (true) {
                    val s = socket.accept()
                    GlobalScope.launch {
                        val context = newSingleThreadContext("$appName-socket-$p")
                        withContext(context) {
                            val connection = (s.getInputStream() to s.getOutputStream())
                                .asConnection(env)
                            connection.registerDefault(createChannel())
                        }
                        context.close()
                    }
                }
            }
        }
        super.run()
    }

    actual override fun embeddedServer(
        port: Int,
        function: Application.() -> Unit
    ): EmbeddedServer<*, *> {
        return embeddedServer(Netty, port) {
            function()
        }
    }

    override fun createRouting(routing: Routing) {
        if (!noHttp) {
            env.logger.info("ServiceApp", "Disabling http endpoints")
            super.createRouting(routing)
        }
        if (enableWebsockets) {
            env.logger.info(
                "ServiceApp",
                "Enabling websocket hosting on /${this.appName.decapitalize()}"
            )
            routing.serveWebsocket(
                "/${this.appName.decapitalize()}",
                this.createChannel(),
                this.env
            )
        }
    }
}
