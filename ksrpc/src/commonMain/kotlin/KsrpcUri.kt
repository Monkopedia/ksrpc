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

import com.monkopedia.ksrpc.internal.HttpSerializedChannel
import com.monkopedia.ksrpc.internal.WebsocketPacketChannel
import io.ktor.client.HttpClient
import io.ktor.client.features.websocket.webSocketSession
import io.ktor.http.URLProtocol
import io.ktor.http.takeFrom
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.Serializable

const val KSRPC_BINARY: String = "KSRPC_BINARY"
const val KSRPC_CHANNEL: String = "KSRPC_CHANNEL"

enum class KsrpcType {
    EXE,
    SOCKET,
    HTTP,
    WEBSOCKET,
    LOCAL
}

@Serializable
data class KsrpcUri(
    val type: KsrpcType,
    val path: String,
) {
    override fun toString(): String {
        return path
    }
}

fun String.toKsrpcUri(): KsrpcUri = when {
    startsWith("http://") -> KsrpcUri(KsrpcType.HTTP, this)
    startsWith("https://") -> KsrpcUri(KsrpcType.HTTP, this)
    startsWith("ksrpc://") -> KsrpcUri(KsrpcType.SOCKET, this)
    startsWith("ws://") -> KsrpcUri(KsrpcType.WEBSOCKET, this)
    startsWith("local://") -> KsrpcUri(KsrpcType.LOCAL, this.substring("local://".length))
    else -> throw IllegalArgumentException("Unable to parse $this")
}

expect suspend fun KsrpcUri.connect(
    env: KsrpcEnvironment,
    clientFactory: () -> HttpClient = { HttpClient { } },
): ChannelClient

internal fun HttpClient.asPacketChannel(baseUrl: String, env: KsrpcEnvironment) =
    HttpSerializedChannel(this, baseUrl.trimEnd('/'), env)

fun HttpClient.asChannel(baseUrl: String, env: KsrpcEnvironment): ChannelClient =
    asPacketChannel(baseUrl, env)

internal suspend fun HttpClient.asWebsocketPackets(
    baseUrl: String,
    env: KsrpcEnvironment,
    scope: CoroutineScope? = null
) = WebsocketPacketChannel(
    scope ?: env.defaultScope,
    webSocketSession {
        url.takeFrom(baseUrl.trimEnd('/'))
        url.protocol = URLProtocol.WS
    },
    env
)

suspend fun HttpClient.asWebsocketChannel(baseUrl: String, env: KsrpcEnvironment): Connection =
    asWebsocketPackets(baseUrl, env).threadSafe()
