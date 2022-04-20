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
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import io.ktor.utils.io.reader
import java.io.InputStream
import java.io.OutputStream
import java.nio.channels.Channels
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.GlobalScope

/**
 * Helper that calls into Pair<ByteReadChannel, ByteWriteChannel>.asConnection.
 */
suspend fun Pair<InputStream, OutputStream>.asConnection(env: KsrpcEnvironment): Connection {
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
    return (input.toByteReadChannel(coroutineContext) to channel).asConnection(env)
}

/**
 * Helper that calls into Pair<ByteReadChannel, ByteWriteChannel>.asJsonRpcConnection.
 */
suspend fun Pair<InputStream, OutputStream>.asJsonRpcConnection(
    env: KsrpcEnvironment
): SingleChannelConnection {
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
