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

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.channels.SerializedService
import com.monkopedia.ksrpc.ksrpcEnvironment
import com.monkopedia.ksrpc.ktor.serve
import com.monkopedia.ksrpc.sockets.withStdInOut
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.BaseApplicationEngine
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.routing.Routing
import io.ktor.server.routing.routing
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking

/**
 * Base class that makes it easy to host a default service on any combination of
 * std in/out, http, and sockets.
 */
abstract class BaseServiceApp internal constructor(val appName: String) : CliktCommand() {
    init {
        isActive = true
    }

    val http by option(
        "-h",
        "--http",
        help = "Host this service as http service on specified port"
    ).int().multiple()
    val stdOut by option("-o", "--stdout", help = "Use stdin/stdout as socket for service").flag()
    val cors by option(
        "-c",
        "--cors",
        help = "Allow CORS from any host for this server"
    ).flag()

    override fun run() {
        runBlocking {
            for (h in http) {
                env.logger.info("ServiceApp", "Hosting http server on $h")
                embeddedServer(h) {
                    configureHttp()
                    routing {
                        createRouting(this)
                    }
                }.start()
            }
            if (stdOut) {
                withStdInOut(env) { connection ->
                    connection.registerDefault(createChannel())
                    awaitCancellation()
                }
            }
            awaitCancellation()
        }
    }

    open fun Application.configureHttp() {
        if (cors) {
            env.logger.info("ServiceApp", "Configuring http for cors")
            install(CORS) {
                anyHost()
            }
        }
    }

    abstract fun embeddedServer(port: Int, function: Application.() -> Unit): BaseApplicationEngine

    protected open fun createRouting(routing: Routing) {
        routing.serve("/${appName.decapitalize()}", createChannel(), env)
    }

    open val env: KsrpcEnvironment<String> by lazy {
        ksrpcEnvironment {}
    }

    abstract fun createChannel(): SerializedService<String>

    companion object {
        internal var isActive = false
    }
}

expect abstract class ServiceApp(appName: String) : BaseServiceApp
