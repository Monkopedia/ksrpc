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
import com.monkopedia.ksrpc.internal.HttpSerializedChannel
import com.monkopedia.ksrpc.internal.ThreadSafeManager.threadSafe
import com.monkopedia.ksrpc.internal.WebsocketPacketChannel
import io.ktor.client.HttpClient
import io.ktor.client.features.websocket.webSocketSession
import io.ktor.http.URLProtocol
import io.ktor.http.takeFrom
import kotlinx.coroutines.CoroutineScope

/**
 * Turn an [HttpClient] into a [ChannelClient] for a specified baseUrl.
 *
 * This is functionally equivalent to baseUrl.toKsrpcUri().connect(env).
 */
fun HttpClient.asConnection(baseUrl: String, env: KsrpcEnvironment): ChannelClient =
    HttpSerializedChannel(this, baseUrl.trimEnd('/'), env).threadSafe<ChannelClient>()

/**
 * Turn an [HttpClient] into a websocket based [Connection] for a specified baseUrl.
 *
 * This is functionally equivalent to baseUrl.toKsrpcUri().connect(env).
 */
suspend fun HttpClient.asWebsocketConnection(baseUrl: String, env: KsrpcEnvironment): Connection {
    val session = webSocketSession {
        url.takeFrom(baseUrl.trimEnd('/'))
        url.protocol = URLProtocol.WS
    }
    return threadSafe<Connection> { context ->
        WebsocketPacketChannel(
            CoroutineScope(context),
            context,
            session,
            env
        )
    }.also {
        (it as SuspendInit).init()
    }
}
