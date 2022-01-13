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
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.plus

internal const val CONTENT_LENGTH = "Content-Length"
internal const val METHOD = "Method"
internal const val INPUT = "Input"
internal const val TYPE = "Type"

internal enum class SendType {
    NORMAL,
    BINARY,
    BINARY_INPUT
}

expect val DEFAULT_DISPATCHER: CoroutineDispatcher

suspend fun SerializedChannel.serve(
    input: ByteReadChannel,
    output: ByteWriteChannel,
    asyncDispatcher: CoroutineDispatcher = DEFAULT_DISPATCHER,
    errorListener: ErrorListener = ErrorListener { }
) {
    coroutineScope {
        ReadWritePacketChannel(input, output).connect(
            this + asyncDispatcher + CoroutineExceptionHandler { _, t ->
                errorListener.onError(t)
            }
        ) {
            this@serve
        }
    }
}

fun Pair<ByteReadChannel, ByteWriteChannel>.asChannel(): SerializedChannel {
    return ReadWritePacketChannel(first, second)
}
