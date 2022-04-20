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
package com.monkopedia.ksrpc.channels

import com.monkopedia.ksrpc.KsrpcEnvironment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Create a [Connection] that communicates over the std in/out streams of this process.
 */
suspend fun stdInConnection(env: KsrpcEnvironment): Connection {
    val input = System.`in`
    val output = System.out
    return (input to output).asConnection(env)
}

/**
 * Create a [Connection] that starts the process and uses the
 * [Process.getInputStream] and [Process.getOutputStream] as the streams for communication
 */
suspend fun ProcessBuilder.asConnection(env: KsrpcEnvironment): Connection {
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

internal inline fun swallow(function: () -> Unit) {
    try {
        function()
    } catch (t: Throwable) {
    }
}

/**
 * Create a [SingleChannelConnection] that communicates over the std in/out streams of this process
 * using jsonrpc.
 */
suspend fun stdInJsonRpcConnection(env: KsrpcEnvironment): SingleChannelConnection {
    val input = System.`in`
    val output = System.out
    return (input to output).asJsonRpcConnection(env)
}

/**
 * Create a [SingleChannelConnection] that starts the process and uses the
 * [Process.getInputStream] and [Process.getOutputStream] as the streams for communication using
 * jsonrpc.
 */
suspend fun ProcessBuilder.asJsonRpcConnection(env: KsrpcEnvironment): SingleChannelConnection {
    val process = redirectInput(ProcessBuilder.Redirect.PIPE)
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .start()
    val input = process.inputStream
    val output = process.outputStream
    return (input to output).asJsonRpcConnection(env).also {
        it.defaultChannel().onClose {
            withContext(Dispatchers.IO) {
                swallow { input.close() }
                swallow { output.close() }
            }
            process.destroyForcibly()
        }
    }
}
