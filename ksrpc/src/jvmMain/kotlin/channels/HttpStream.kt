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

import com.monkopedia.ksrpc.ERROR_PREFIX
import com.monkopedia.ksrpc.KSRPC_BINARY
import com.monkopedia.ksrpc.KSRPC_CHANNEL
import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.RpcFailure
import com.monkopedia.ksrpc.RpcService
import com.monkopedia.ksrpc.asString
import com.monkopedia.ksrpc.internal.HostSerializedChannelImpl
import com.monkopedia.ksrpc.internal.WebsocketPacketChannel
import com.monkopedia.ksrpc.serialized
import io.ktor.http.HttpStatusCode
import io.ktor.http.decodeURLPart
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import io.ktor.server.websocket.webSocket
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.copyTo
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json

suspend inline fun <reified T : RpcService> Routing.serve(
    basePath: String,
    service: T,
    env: KsrpcEnvironment
) = serve(basePath, service.serialized<T>(env), env)

suspend inline fun <reified T : RpcService> Routing.serveWebsocket(
    basePath: String,
    service: T,
    env: KsrpcEnvironment
) = serveWebsocket(basePath, service.serialized<T>(env), env)

suspend fun serve(
    basePath: String,
    service: SerializedService,
    env: KsrpcEnvironment
): Routing.() -> Unit {
    val channel = HostSerializedChannelImpl(env).also {
        it.registerDefault(service)
    }
    return {
        serve(basePath, channel, env)
    }
}

fun Routing.serve(
    basePath: String,
    channel: SerializedChannel,
    env: KsrpcEnvironment
) {
    val baseStripped = basePath.trimEnd('/')
    post("$baseStripped/call/{method}") {
        try {
            val method = call.parameters["method"]?.decodeURLPart() ?: error("Missing method")
            val content = if (call.request.headers[KSRPC_BINARY]?.toBoolean() == true) {
                CallData.create(call.receive<ByteReadChannel>())
            } else {
                CallData.create(call.receive<String>())
            }
            val channelId = call.request.headers[KSRPC_CHANNEL] ?: ChannelClient.DEFAULT
            val response = channel.call(ChannelId(channelId), method, content)
            if (response.isBinary) {
                call.response.headers.append(KSRPC_BINARY, "true")
                call.respondBytesWriter {
                    response.readBinary().copyTo(this)
                }
            } else {
                call.respond(response.readSerialized())
            }
        } catch (t: Throwable) {
            env.errorListener.onError(t)
            call.respond(
                ERROR_PREFIX + Json.encodeToString(RpcFailure.serializer(), RpcFailure(t.asString))
            )
            call.response.status(HttpStatusCode.InternalServerError)
        }
    }
}

fun Routing.serveWebsocket(
    basePath: String,
    channel: SerializedService,
    env: KsrpcEnvironment
) {
    val baseStripped = basePath.trimEnd('/')
    webSocket(baseStripped) {
        coroutineScope {
            val wb = WebsocketPacketChannel(this, coroutineContext, this@webSocket, env)
            wb.init()
            wb.connect {
                channel
            }
        }
    }
}
