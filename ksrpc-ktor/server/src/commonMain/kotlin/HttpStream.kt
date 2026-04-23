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
@file:OptIn(KsrpcInternal::class)

package com.monkopedia.ksrpc.ktor

import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.RpcEndpointException
import com.monkopedia.ksrpc.RpcFailure
import com.monkopedia.ksrpc.RpcService
import com.monkopedia.ksrpc.annotation.KsrpcInternal
import com.monkopedia.ksrpc.asString
import com.monkopedia.ksrpc.binary.ktor.asRpcBinaryData
import com.monkopedia.ksrpc.channels.CallData
import com.monkopedia.ksrpc.channels.ChannelClient
import com.monkopedia.ksrpc.channels.ChannelId
import com.monkopedia.ksrpc.channels.SerializedChannel
import com.monkopedia.ksrpc.channels.SerializedService
import com.monkopedia.ksrpc.internal.ENDPOINT_NOT_FOUND_PREFIX
import com.monkopedia.ksrpc.internal.ERROR_PREFIX
import com.monkopedia.ksrpc.internal.HostSerializedChannelImpl
import com.monkopedia.ksrpc.serialized
import io.ktor.http.HttpStatusCode
import io.ktor.http.decodeURLPart
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.Routing
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.post
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

private const val KSRPC_BINARY = "binary"
private const val KSRPC_CHANNEL = "channel"

inline fun <reified T : RpcService> Routing.serveHttp(
    basePath: String,
    service: T,
    env: KsrpcEnvironment<String>
) {
    val serializedService = service.serialized(env)
    serveHttp(basePath, serializedService, env)
}

fun Routing.serveHttp(
    basePath: String,
    serializedService: SerializedService<String>,
    env: KsrpcEnvironment<String>
) {
    val channel = HostSerializedChannelImpl(env).also {
        env.defaultScope.launch {
            it.registerDefault(serializedService)
        }
    }
    serveHttp(basePath, channel, env)
}

fun Routing.serveHttp(
    basePath: String,
    channel: SerializedChannel<String>,
    env: KsrpcEnvironment<String>
) {
    val baseStripped = basePath.trimEnd('/')
    post("$baseStripped/call/") {
        runCatching(env) {
            execCall(channel, "")
        }
    }
    post("$baseStripped/call/{method}") {
        runCatching(env) {
            val method = call.parameters["method"]?.decodeURLPart() ?: error("Missing method")
            execCall(channel, method)
        }
    }
}

private suspend fun RoutingContext.execCall(channel: SerializedChannel<String>, method: String) {
    val content = if (call.request.headers[KSRPC_BINARY]?.toBoolean() == true) {
        CallData.createBinary(call.receive<ByteReadChannel>().asRpcBinaryData())
    } else {
        CallData.create(call.receive<String>())
    }
    val channelId = call.request.headers[KSRPC_CHANNEL] ?: ChannelClient.DEFAULT
    channel.env.logger.debug("HttpChannel", "Executing call $channelId/$method")
    // HTTP has no wire-level request correlation id the handler would need to see; each
    // request is a synchronous round trip. Pass null as the callId — the server-side
    // RpcMethod.call will still install CurrentRpcCallElement so handlers can introspect
    // the method, with id == null indicating "no transport-level call id".
    val response = channel.call(ChannelId(channelId), method, content, callId = null)
    if (response.isBinary) {
        channel.env.logger.debug(
            "HttpChannel",
            "Responding to $channelId/$method with binary content"
        )
        call.response.headers.append(KSRPC_BINARY, "true")
        call.respondBytesWriter {
            response.readBinary().transferTo { bytes, offset, length ->
                writeFully(bytes, offset, length)
            }
        }
    } else {
        channel.env.logger.debug(
            "HttpChannel",
            "Responding to $channelId/$method with serialized content"
        )
        call.respond(response.readSerialized())
    }
}

private suspend inline fun RoutingContext.runCatching(
    env: KsrpcEnvironment<String>,
    exec: suspend () -> Unit
) {
    try {
        exec()
    } catch (t: Throwable) {
        env.errorListener.onError(t)
        val prefix = if (t is RpcEndpointException) {
            ENDPOINT_NOT_FOUND_PREFIX
        } else {
            ERROR_PREFIX
        }
        call.respond(
            prefix + Json.encodeToString(RpcFailure.serializer(), RpcFailure(t.asString))
        )
        call.response.status(HttpStatusCode.InternalServerError)
    }
}
