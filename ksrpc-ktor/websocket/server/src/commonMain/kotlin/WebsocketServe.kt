/*
 * Copyright (C) 2026 Jason Monk <monkopedia@gmail.com>
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
import com.monkopedia.ksrpc.RpcService
import com.monkopedia.ksrpc.channels.CallData
import com.monkopedia.ksrpc.channels.ChannelClient
import com.monkopedia.ksrpc.channels.ChannelId
import com.monkopedia.ksrpc.channels.RpcCallId
import com.monkopedia.ksrpc.channels.SerializedChannel
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
    env: KsrpcEnvironment<String>
) = serveWebsocket(basePath, service.serialized(env), env)

fun Routing.serveWebsocket(
    basePath: String,
    channel: SerializedService<String>,
    env: KsrpcEnvironment<String>
) {
    val baseStripped = basePath.trimEnd('/')
    webSocket(baseStripped) {
        coroutineScope {
            val wb = WebsocketPacketChannel(this, this@webSocket, env)
            wb.connect<String> {
                channel
            }
        }
    }
}

/**
 * Mirrors [Routing.serve]'s [SerializedChannel] overload for the websocket transport.
 *
 * The [SerializedService]-taking overload above is the convenience entry point that wraps a
 * single root service; this overload accepts a pre-built [SerializedChannel] for callers
 * that own the dispatch surface (e.g. a [com.monkopedia.ksrpc.internal.HostSerializedChannelImpl]
 * with multiple registered services). Calls received over the socket are dispatched through
 * the supplied [channel].
 */
fun Routing.serveWebsocket(
    basePath: String,
    channel: SerializedChannel<String>,
    env: KsrpcEnvironment<String>
) {
    val baseStripped = basePath.trimEnd('/')
    webSocket(baseStripped) {
        coroutineScope {
            val wb = WebsocketPacketChannel(this, this@webSocket, env)
            wb.connect<String> {
                ChannelBackedSerializedService(channel)
            }
        }
    }
}

/**
 * Adapter exposing a [SerializedChannel] as a [SerializedService] bound to the channel's
 * default channel id. Used by the [SerializedChannel] overload of [serveWebsocket] to
 * register the supplied channel through the websocket connection's [connect] entry point.
 *
 * If [channel] also implements [ChannelClient], its [ChannelClient.defaultChannel] is used
 * directly so any extra wiring (close hooks, sub-service routing) is preserved. When the
 * underlying type does not expose [ChannelClient], the adapter forwards calls to the channel
 * using the [ChannelClient.DEFAULT] channel id.
 */
private class ChannelBackedSerializedService(private val channel: SerializedChannel<String>) :
    SerializedService<String> {
    override val env: KsrpcEnvironment<String>
        get() = channel.env

    override suspend fun call(
        endpoint: String,
        input: CallData<String>,
        callId: RpcCallId?
    ): CallData<String> = if (channel is ChannelClient<String>) {
        channel.defaultChannel().call(endpoint, input, callId)
    } else {
        channel.call(ChannelId(ChannelClient.DEFAULT), endpoint, input, callId)
    }

    override suspend fun close() {
        channel.close()
    }

    override suspend fun onClose(onClose: suspend () -> Unit) {
        channel.onClose(onClose)
    }
}
