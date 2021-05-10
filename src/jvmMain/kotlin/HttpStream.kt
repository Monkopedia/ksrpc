/*
 * Copyright 2020 Jason Monk
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

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.readText
import io.ktor.http.decodeURLPart
import io.ktor.request.receive
import io.ktor.request.receiveChannel
import io.ktor.response.respond
import io.ktor.response.respondBytesWriter
import io.ktor.routing.Routing
import io.ktor.routing.post
import io.ktor.utils.io.copyTo
import io.ktor.websocket.webSocket
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.json.Json

fun Routing.serve(
    basePath: String,
    channel: SerializedChannel,
    errorListener: ErrorListener = ErrorListener { },
) {
    val baseStripped = basePath.trimEnd('/')
    post("$baseStripped/call/{method}") {
        try {
            val method = call.parameters["method"]?.decodeURLPart() ?: error("Missing method")
            val content = call.receive<String>()
            val response = channel.call(method, content)
            call.respond(response)
        } catch (t: Throwable) {
            errorListener.onError(t)
            call.respond(
                ERROR_PREFIX + Json.encodeToString(RpcFailure.serializer(), RpcFailure(t.asString))
            )
            call.response.status(HttpStatusCode.InternalServerError)
        }
    }
    post("$baseStripped/binary/{method}") {
        try {
            val method = call.parameters["method"]?.decodeURLPart() ?: error("Missing method")
            val content = call.receive<String>()
            val response = channel.callBinary(method, content)
            call.respondBytesWriter {
                response.copyTo(this)
            }
        } catch (t: Throwable) {
            errorListener.onError(t)
            call.respond(
                ERROR_PREFIX + Json.encodeToString(RpcFailure.serializer(), RpcFailure(t.asString))
            )
            call.response.status(HttpStatusCode.InternalServerError)
        }
    }
    post("$baseStripped/binaryInput/{method}") {
        try {
            val method = call.parameters["method"]?.decodeURLPart() ?: error("Missing method")
            val content = call.receiveChannel()
            val response = channel.callBinaryInput(method, content)
            call.respond(response)
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
    channel: SerializedChannel,
    errorListener: ErrorListener = ErrorListener { },
) {
    val baseStripped = basePath.trimEnd('/')
    webSocket(baseStripped) {
        while (!incoming.isClosedForReceive) {
            try {
                when (enumValueOf<SendType>(incoming.receive().expectText())) {
                    SendType.NORMAL -> {
                        val endpoint = incoming.receive().expectText()
                        val input = incoming.receive().expectText()
                        val response = channel.call(endpoint, input)
                        outgoing.send(Frame.Text(response))
                    }
                    SendType.BINARY -> {
                        val endpoint = incoming.receive().expectText()
                        val input = incoming.receive().expectText()
                        val response = channel.callBinary(endpoint, input)
                        response.toFrameFlow().collect {
                            outgoing.send(it)
                        }
                    }
                    SendType.BINARY_INPUT -> {
                        val endpoint = incoming.receive().expectText()
                        val input = incoming.toReadChannel {
                        }
                        val response = channel.callBinaryInput(endpoint, input)
                        outgoing.send(Frame.Text(response))
                    }
                }
            } catch (_: ClosedReceiveChannelException) {
                // Don't mind, just done with this channel.
            } catch (t: Throwable) {
                errorListener.onError(t)
                val failure = RpcFailure(t.asString)
                outgoing.send(
                    Frame.Text(
                        ERROR_PREFIX + Json.encodeToString(RpcFailure.serializer(), failure)
                    )
                )
            }
        }
    }
}

private fun Frame.expectText(): String {
    if (this is Frame.Text) {
        return readText()
    } else {
        throw IllegalStateException("Unexpected frame $this")
    }
}
