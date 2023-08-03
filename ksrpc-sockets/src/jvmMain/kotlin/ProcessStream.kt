/**
 * Copyright (C) 2022 Jason Monk <monkopedia@gmail.com>
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
package com.monkopedia.ksrpc.sockets

import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.channels.Connection
import com.monkopedia.ksrpc.sockets.internal.swallow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual suspend inline fun withStdInOut(
    ksrpcEnvironment: KsrpcEnvironment<String>,
    withConnection: (Connection<String>) -> Unit
) {
    val input = System.`in`
    val output = System.out
    val connection = (input to output).asConnection(ksrpcEnvironment)
    try {
        withConnection(connection)
    } finally {
        swallow { connection.close() }
    }
}

/**
 * Create a [Connection] that starts the process and uses the
 * [Process.getInputStream] and [Process.getOutputStream] as the streams for communication
 */
suspend fun ProcessBuilder.asConnection(env: KsrpcEnvironment<String>): Connection<String> {
    val process = redirectInput(ProcessBuilder.Redirect.PIPE)
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .start()
    val input = process.inputStream
    val output = process.outputStream
    return (input to output).asConnection(env).also {
        it.onClose {
            withContext(Dispatchers.IO) {
                swallow { input.close() }
                swallow { output.close() }
            }
            process.destroyForcibly()
        }
    }
}
