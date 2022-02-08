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
    return threadSafe { context ->
        WebsocketPacketChannel(
            CoroutineScope(context),
            context,
            session,
            env
        )
    }.also {
        it.init()
    }
}