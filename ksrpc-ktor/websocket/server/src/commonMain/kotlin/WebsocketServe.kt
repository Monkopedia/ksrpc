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
