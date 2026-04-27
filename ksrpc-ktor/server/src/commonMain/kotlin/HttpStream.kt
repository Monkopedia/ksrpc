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
import com.monkopedia.ksrpc.KsrpcException
import com.monkopedia.ksrpc.RpcEndpointException
import com.monkopedia.ksrpc.RpcService
import com.monkopedia.ksrpc.annotation.KsrpcInternal
import com.monkopedia.ksrpc.binary.ktor.asRpcBinaryData
import com.monkopedia.ksrpc.channels.CallData
import com.monkopedia.ksrpc.channels.ChannelClient
import com.monkopedia.ksrpc.channels.ChannelId
import com.monkopedia.ksrpc.channels.SerializedChannel
import com.monkopedia.ksrpc.channels.SerializedService
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

private const val KSRPC_BINARY = "binary"
private const val KSRPC_CHANNEL = "channel"

/**
 * Header carrying the original ksrpc error code when the wire status maps to the default
 * 500 fallback (i.e. the code is not present in the configured `errorCodeToHttpStatus` map).
 * The client side uses this to recover the exact code for `@KsError`-bound payload routing
 * on the receive path. Match this constant on both ends — see also the duplicate definition
 * in `ksrpc-ktor-client`'s `HttpSerializedChannel.kt`.
 */
const val KSRPC_ERROR_CODE_HEADER: String = "X-Ksrpc-Error-Code"

/**
 * Header carrying the human-readable error message that pairs with the ksrpc error code on
 * the wire. The body slot carries the typed `errorData` payload; the message moves to a
 * header so it survives transports that strip/escape the body, and so the client can
 * recover it cleanly even when the body decode fails.
 */
const val KSRPC_ERROR_MESSAGE_HEADER: String = "X-Ksrpc-Error-Message"

/**
 * Default mapping from ksrpc error codes to HTTP status codes used by the HTTP transport.
 *   - [KsrpcException.ENDPOINT_NOT_FOUND_CODE] (`-32601`) -> 404
 *   - [KsrpcException.INTERNAL_ERROR_CODE] (`-32603`) -> 500
 *
 * Codes not present in the configured map default to status 500, with the original code
 * carried in [KSRPC_ERROR_CODE_HEADER]. The error response body always carries the
 * wire-format-encoded error payload (or empty when no payload is attached).
 *
 * Pass the same map on both ends so the round-trip preserves user-defined codes.
 */
val DEFAULT_KSRPC_ERROR_CODE_TO_HTTP_STATUS: Map<Int, Int> = mapOf(
    KsrpcException.ENDPOINT_NOT_FOUND_CODE to 404,
    KsrpcException.INTERNAL_ERROR_CODE to 500
)

inline fun <reified T : RpcService> Routing.serveHttp(
    basePath: String,
    service: T,
    env: KsrpcEnvironment<String>,
    errorCodeToHttpStatus: Map<Int, Int> = DEFAULT_KSRPC_ERROR_CODE_TO_HTTP_STATUS
) {
    val serializedService = service.serialized(env)
    serveHttp(basePath, serializedService, env, errorCodeToHttpStatus)
}

fun Routing.serveHttp(
    basePath: String,
    serializedService: SerializedService<String>,
    env: KsrpcEnvironment<String>,
    errorCodeToHttpStatus: Map<Int, Int> = DEFAULT_KSRPC_ERROR_CODE_TO_HTTP_STATUS
) {
    val channel = HostSerializedChannelImpl(env).also {
        env.defaultScope.launch {
            it.registerDefault(serializedService)
        }
    }
    serveHttp(basePath, channel, env, errorCodeToHttpStatus)
}

fun Routing.serveHttp(
    basePath: String,
    channel: SerializedChannel<String>,
    env: KsrpcEnvironment<String>,
    errorCodeToHttpStatus: Map<Int, Int> = DEFAULT_KSRPC_ERROR_CODE_TO_HTTP_STATUS
) {
    val baseStripped = basePath.trimEnd('/')
    post("$baseStripped/call/") {
        runCatching(env, errorCodeToHttpStatus) {
            execCall(channel, "", errorCodeToHttpStatus)
        }
    }
    post("$baseStripped/call/{method}") {
        runCatching(env, errorCodeToHttpStatus) {
            val method = call.parameters["method"]?.decodeURLPart() ?: error("Missing method")
            execCall(channel, method, errorCodeToHttpStatus)
        }
    }
}

private suspend fun RoutingContext.execCall(
    channel: SerializedChannel<String>,
    method: String,
    errorCodeToHttpStatus: Map<Int, Int>
) {
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
    when {
        response is CallData.Error<String> -> {
            channel.env.logger.debug(
                "HttpChannel",
                "Responding to $channelId/$method with error ${response.errorCode}"
            )
            val mappedStatus = errorCodeToHttpStatus[response.errorCode]
            if (mappedStatus == null) {
                // Fallback: status 500 + original code in header so the client can recover
                // the user-defined code for typed-payload routing.
                call.response.headers.append(
                    KSRPC_ERROR_CODE_HEADER,
                    response.errorCode.toString()
                )
            }
            call.response.headers.append(
                KSRPC_ERROR_MESSAGE_HEADER,
                response.errorMessage.replace('\n', ' ')
            )
            call.respond(
                HttpStatusCode.fromValue(mappedStatus ?: 500),
                response.errorData ?: ""
            )
        }

        response.isBinary -> {
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
        }

        else -> {
            channel.env.logger.debug(
                "HttpChannel",
                "Responding to $channelId/$method with serialized content"
            )
            call.respond(response.readSerialized())
        }
    }
}

private suspend inline fun RoutingContext.runCatching(
    env: KsrpcEnvironment<String>,
    errorCodeToHttpStatus: Map<Int, Int>,
    exec: suspend () -> Unit
) {
    try {
        exec()
    } catch (t: Throwable) {
        env.errorListener.onError(t)
        val code = when (t) {
            is RpcEndpointException -> KsrpcException.ENDPOINT_NOT_FOUND_CODE
            is KsrpcException -> t.code
            else -> KsrpcException.INTERNAL_ERROR_CODE
        }
        val mappedStatus = errorCodeToHttpStatus[code]
        if (mappedStatus == null) {
            call.response.headers.append(KSRPC_ERROR_CODE_HEADER, code.toString())
        }
        // Concise message only — full stack stays server-side via the logger above,
        // not in HTTP headers or response body. Header-safe (no embedded newlines).
        val concise = (t.message ?: t.toString()).replace('\n', ' ')
        call.response.headers.append(KSRPC_ERROR_MESSAGE_HEADER, concise)
        call.respond(HttpStatusCode.fromValue(mappedStatus ?: 500), concise)
    }
}
