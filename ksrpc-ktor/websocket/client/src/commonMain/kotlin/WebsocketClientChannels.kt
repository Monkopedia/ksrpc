/**
 * Copyright (C) 2025 Jason Monk <monkopedia@gmail.com>
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
package com.monkopedia.ksrpc.ktor.websocket

import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.channels.Connection
import com.monkopedia.ksrpc.ktor.websocket.internal.WebsocketPacketChannel
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.http.URLProtocol
import io.ktor.http.takeFrom
import kotlinx.coroutines.CoroutineScope

/**
 * Turn an [HttpClient] into a websocket based [Connection] for a specified baseUrl.
 *
 * This is functionally equivalent to baseUrl.toKsrpcUri().connect(env).
 */
suspend fun HttpClient.asWebsocketConnection(
    baseUrl: String,
    env: KsrpcEnvironment<String>
): Connection<String> {
    val session = webSocketSession {
        url.takeFrom(baseUrl.trimEnd('/'))
        url.protocol = URLProtocol.WS
    }
    return WebsocketPacketChannel(
        CoroutineScope(coroutineContext),
        session,
        env
    )
}
