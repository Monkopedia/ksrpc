package com.monkopedia.ksrpc.ktor.websocket

import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.RpcService
import com.monkopedia.ksrpc.channels.SerializedService
import com.monkopedia.ksrpc.channels.connect
import com.monkopedia.ksrpc.ktor.websocket.internal.WebsocketPacketChannel
import com.monkopedia.ksrpc.serialized
import io.ktor.server.routing.Routing
import io.ktor.server.websocket.webSocket
import kotlinx.coroutines.coroutineScope

inline fun <reified T : RpcService> Routing.serveWebsocket(
    basePath: String,
    service: T,
    env: KsrpcEnvironment
) = serveWebsocket(basePath, service.serialized(env), env)

fun Routing.serveWebsocket(
    basePath: String,
    channel: SerializedService,
    env: KsrpcEnvironment
) {
    val baseStripped = basePath.trimEnd('/')
    webSocket(baseStripped) {
        coroutineScope {
            val wb = WebsocketPacketChannel(this, this@webSocket, env)
            wb.connect {
                channel
            }
        }
    }
}