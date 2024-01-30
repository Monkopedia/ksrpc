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
package com.monkopedia.ksrpc.jsonrpc

import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.channels.SingleChannelConnection
import com.monkopedia.ksrpc.sockets.internal.swallow
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import io.ktor.utils.io.reader
import java.io.InputStream
import java.io.OutputStream
import java.nio.channels.Channels
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.withContext

/**
 * Helper that calls into Pair<ByteReadChannel, ByteWriteChannel>.asJsonRpcConnection.
 */
suspend fun Pair<InputStream, OutputStream>.asJsonRpcConnection(
    env: KsrpcEnvironment<String>
): SingleChannelConnection<String> {
    val (input, output) = this
    val channel = GlobalScope.reader(coroutineContext) {
        val outputChannel = Channels.newChannel(output)
        while (!channel.isClosedForRead) {
            channel.read { buffer ->
                outputChannel.write(buffer)
                output.flush()
            }
        }
    }.channel
    return (input.toByteReadChannel(coroutineContext) to channel).asJsonRpcConnection(env)
}

/**
 * Create a [SingleChannelConnection] that communicates over the std in/out streams of this process
 * using jsonrpc.
 */
suspend fun stdInJsonRpcConnection(env: KsrpcEnvironment<String>): SingleChannelConnection<String> {
    val input = System.`in`
    val output = System.out
    return (input to output).asJsonRpcConnection(env)
}

/**
 * Create a [SingleChannelConnection] that starts the process and uses the
 * [Process.getInputStream] and [Process.getOutputStream] as the streams for communication using
 * jsonrpc.
 */
suspend fun ProcessBuilder.asJsonRpcConnection(
    env: KsrpcEnvironment<String>
): SingleChannelConnection<String> {
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
