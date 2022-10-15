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
package com.monkopedia.ksrpc.server

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.channels.SerializedService
import com.monkopedia.ksrpc.ksrpcEnvironment
import com.monkopedia.ksrpc.ktor.serve
import com.monkopedia.ksrpc.sockets.asConnection
import com.monkopedia.ksrpc.sockets.stdInConnection
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.routing.Routing
import io.ktor.server.routing.routing
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * Base class that makes it easy to host a default service on any combination of
 * std in/out, http, and sockets.
 */
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
                            val connection = (s.getInputStream() to s.getOutputStream())
                                .asConnection(env)
                            connection.registerDefault(createChannel())
                        }
                        context.close()
                    }
                }
            }
        }
        runBlocking {
            for (h in http) {
                embeddedServer(Netty, h) {
                    install(CORS) {
                        anyHost()
                    }
                    routing {
                        createRouting()
                    }
                }.start()
            }
            if (stdOut) {
                stdInConnection(env).registerDefault(createChannel())
            }
        }
    }

    protected open fun Routing.createRouting() {
        serve("/${appName.decapitalize()}", createChannel(), env)(this)
    }

    open val env: KsrpcEnvironment by lazy {
        ksrpcEnvironment {}
    }

    abstract fun createChannel(): SerializedService

    companion object {
        internal var isActive = false
    }
}
