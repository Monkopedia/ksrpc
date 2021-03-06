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

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import io.ktor.application.install
import io.ktor.features.CORS
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlin.system.exitProcess
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

abstract class ServiceApp(val appName: String) : CliktCommand() {
    init {
        isActive = true
    }

    val port by option(
        "-p",
        "--port",
        help = "Host this service as ksrpc service on specified port"
    ).int().multiple()
    val http by option(
        "-h",
        "--http",
        help = "Host this service as http service on specified port"
    ).int().multiple()
    val stdOut by option("-o", "--stdout", help = "Use stdin/stdout as socket for service").flag()

    override fun run() {
        if (!stdOut && port.isEmpty() && http.isEmpty()) {
            println("No output mechanism specified, exiting")
            exitProcess(1)
        }
        for (p in port) {
            thread(start = true) {
                val socket = ServerSocket(p)
                while (true) {
                    val s = socket.accept()
                    GlobalScope.launch {
                        val context = newSingleThreadContext("$appName-socket-$p")
                        withContext(context) {
                            createChannel().serve(s.getInputStream(), s.getOutputStream())
                        }
                        context.close()
                    }
                }
            }
        }
        for (h in http) {
            embeddedServer(Netty, h) {
                install(CORS) {
                    anyHost()
                }
                routing {
                    serve("/${appName.decapitalize()}", createChannel())
                }
            }.start()
        }
        if (stdOut) {
            runBlocking {
                createChannel().serveOnStd()
            }
        }
    }

    abstract fun createChannel(): SerializedChannel

    companion object {
        internal var isActive = false
    }
}
