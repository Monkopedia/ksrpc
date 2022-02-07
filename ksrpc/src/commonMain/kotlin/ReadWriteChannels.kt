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
package com.monkopedia.ksrpc

import com.monkopedia.ksrpc.internal.ReadWritePacketChannel
import com.monkopedia.ksrpc.internal.ThreadSafeManager.threadSafe
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.plus

internal const val CONTENT_LENGTH = "Content-Length"
internal const val METHOD = "Method"
internal const val INPUT = "Input"
internal const val TYPE = "Type"
internal const val CHANNEL = "Channel"

internal enum class SendType {
    NORMAL,
    BINARY,
    BINARY_INPUT
}

expect val DEFAULT_DISPATCHER: CoroutineDispatcher

suspend fun SerializedService.defaultHosting(
    input: ByteReadChannel,
    output: ByteWriteChannel,
    env: KsrpcEnvironment
) {
    threadSafe<Connection> { context ->
        ReadWritePacketChannel(CoroutineScope(context), context, input, output, env)
    }.also {
        it.init()
    }.connect {
        this@defaultHosting
    }
}

suspend fun Pair<ByteReadChannel, ByteWriteChannel>.asChannel(
    env: KsrpcEnvironment
): Connection {
    return threadSafe<Connection> { context ->
        ReadWritePacketChannel(CoroutineScope(context), context, first, second, env)
    }.also {
        it.init()
    }
}
