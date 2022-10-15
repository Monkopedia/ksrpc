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
suspend fun HttpClient.asWebsocketConnection(baseUrl: String, env: KsrpcEnvironment): Connection {
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
