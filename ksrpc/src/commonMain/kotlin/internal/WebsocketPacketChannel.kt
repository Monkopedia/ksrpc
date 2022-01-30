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
package com.monkopedia.ksrpc.internal

import com.monkopedia.ksrpc.ErrorListener
import io.ktor.http.cio.websocket.DefaultWebSocketSession
import io.ktor.http.cio.websocket.close
import kotlinx.coroutines.CloseableCoroutineDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.StringFormat
import kotlinx.serialization.json.Json

internal suspend fun WebsocketPacketChannel(
    scope: CoroutineScope,
    errorListener: ErrorListener,
    socketSession: DefaultWebSocketSession,
    format: StringFormat = Json,
): WebsocketPacketChannel {
    val thread = maybeCreateChannelThread()
    return withContext(thread) {
        WebsocketPacketChannel(scope, errorListener, socketSession, thread, format).also {
            it.onClose {
                (thread as? CloseableCoroutineDispatcher)?.close()
            }
        }
    }
}

internal class WebsocketPacketChannel(
    scope: CoroutineScope,
    errorListener: ErrorListener,
    private val socketSession: DefaultWebSocketSession,
    channelThread: CoroutineDispatcher,
    format: StringFormat = Json,
) : PacketChannelBase(scope, errorListener, format, channelThread) {
    private val sendLock = Mutex()
    private val receiveLock = Mutex()

    override suspend fun send(packet: Packet) {
        sendLock.withLock {
            socketSession.send(packet)
        }
    }

    override suspend fun receive(): Packet {
        receiveLock.lock(null)
        return socketSession.receivePacket {
            receiveLock.unlock(null)
        }
    }

    override suspend fun close() {
        super.close()
        socketSession.close()
    }
}
