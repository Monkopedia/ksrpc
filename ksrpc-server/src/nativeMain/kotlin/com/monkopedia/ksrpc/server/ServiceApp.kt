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

import io.ktor.server.application.Application
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import platform.posix.exit

/**
 * Base class that makes it easy to host a default service on any combination of
 * std in/out, http, and sockets.
 */
actual abstract class ServiceApp actual constructor(appName: String) : BaseServiceApp(appName) {

    override fun run() {
        if (!stdOut && http.isEmpty()) {
            println("No output mechanism specified, exiting")
            exit(1)
        }

        super.run()
    }

    actual override fun embeddedServer(
        port: Int,
        function: Application.() -> Unit
    ): EmbeddedServer<*, *> {
        return embeddedServer(CIO, port) {
            function()
        }
    }
}
