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
package com.monkopedia.ksrpc.sockets

import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.channels.Connection
import com.monkopedia.ksrpc.sockets.internal.ReadWritePacketChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineScope

internal const val TYPE = "Type"
internal const val CHANNEL = "Channel"
internal const val MESSAGE = "Message"

/**
 * Create a [Connection] for the given input/output channel.
 */
suspend fun Pair<ByteReadChannel, ByteWriteChannel>.asConnection(
    env: KsrpcEnvironment
): Connection {
    return ReadWritePacketChannel(
        CoroutineScope(coroutineContext),
        first,
        second,
        env
    )
}