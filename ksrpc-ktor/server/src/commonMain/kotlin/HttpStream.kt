/**
 * Copyright (C) 2024 Jason Monk <monkopedia@gmail.com>
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
package com.monkopedia.ksrpc.ktor

import com.monkopedia.ksrpc.*
import com.monkopedia.ksrpc.channels.*
import com.monkopedia.ksrpc.internal.HostSerializedChannelImpl
import io.ktor.http.HttpStatusCode
import io.ktor.http.decodeURLPart
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.Routing
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.post
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.copyTo
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

private const val KSRPC_BINARY = "binary"
private const val KSRPC_CHANNEL = "channel"

inline fun <reified T : RpcService> Routing.serve(
    basePath: String,
    service: T,
    env: KsrpcEnvironment<String>
) {
    val serializedService = service.serialized(env)
    serve(basePath, serializedService, env)
}

fun Routing.serve(
    basePath: String,
    serializedService: SerializedService<String>,
    env: KsrpcEnvironment<String>
) {
    val channel = HostSerializedChannelImpl(env).also {
        env.defaultScope.launch {
            it.registerDefault(serializedService)
        }
    }
    serve(basePath, channel, env)
}

fun Routing.serve(
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

private suspend fun RoutingContext.execCall(
    channel: SerializedChannel<String>,
    method: String
) {
    val content = if (call.request.headers[KSRPC_BINARY]?.toBoolean() == true) {
        CallData.createBinary(call.receive<ByteReadChannel>())
    } else {
        CallData.create(call.receive<String>())
    }
    val channelId = call.request.headers[KSRPC_CHANNEL] ?: ChannelClient.DEFAULT
    channel.env.logger.debug("HttpChannel", "Executing call $channelId/$method")
    val response = channel.call(ChannelId(channelId), method, content)
    if (response.isBinary) {
        channel.env.logger.debug(
            "HttpChannel",
            "Responding to $channelId/$method with binary content"
        )
        call.response.headers.append(KSRPC_BINARY, "true")
        call.respondBytesWriter {
            response.readBinary().copyTo(this)
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
        call.respond(
            ERROR_PREFIX + Json.encodeToString(RpcFailure.serializer(), RpcFailure(t.asString))
        )
        call.response.status(HttpStatusCode.InternalServerError)
    }
}
