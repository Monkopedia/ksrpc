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

import com.monkopedia.ksrpc.internal.HostSerializedChannelImpl
import com.monkopedia.ksrpc.internal.WebsocketPacketChannel
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.http.decodeURLPart
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.response.respondBytesWriter
import io.ktor.routing.Routing
import io.ktor.routing.post
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.copyTo
import io.ktor.websocket.webSocket
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.plus
import kotlinx.serialization.StringFormat
import kotlinx.serialization.json.Json

suspend fun serve(
    basePath: String,
    service: SerializedService,
    errorListener: ErrorListener = ErrorListener { },
    format: StringFormat = Json
): Routing.() -> Unit {
    val channel = HostSerializedChannelImpl(errorListener, format).also {
        it.registerDefault(service)
    }
    return {
        serve(basePath, channel, errorListener)
    }
}

fun Routing.serve(
    basePath: String,
    channel: SerializedChannel,
    errorListener: ErrorListener = ErrorListener { }
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
            errorListener.onError(t)
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
    errorListener: ErrorListener = ErrorListener { }
) {
    val baseStripped = basePath.trimEnd('/')
    webSocket(baseStripped) {
        coroutineScope {
            val wb = WebsocketPacketChannel(this, errorListener, this@webSocket)
            wb.connect(
                CoroutineScope(this.coroutineContext) + CoroutineExceptionHandler { _, t ->
                    errorListener.onError(t)
                }
            ) {
                channel
            }
        }
        // while (!incoming.isClosedForReceive) {
        //     try {
        //         val packet = receivePacket {
        //         }
        //         val endpoint = packet.endpoint
        //         val callData = packet.data
        //         val response = channel.call(endpoint, callData)
        //         send(Packet(false, endpoint, response))
        //     } catch (_: ClosedReceiveChannelException) {
        //         // Don't mind, just done with this channel.
        //     } catch (t: Throwable) {
        //         errorListener.onError(t)
        //         val failure = RpcFailure(t.asString)
        //         send(
        //             Frame.Text(
        //                 ERROR_PREFIX + Json.encodeToString(RpcFailure.serializer(), failure)
        //             )
        //         )
        //     }
        // }
    }
}
