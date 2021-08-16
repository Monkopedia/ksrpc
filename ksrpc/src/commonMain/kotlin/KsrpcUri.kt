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

import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.features.websocket.webSocketSession
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.cio.websocket.close
import io.ktor.http.cio.websocket.readText
import io.ktor.http.cio.websocket.send
import io.ktor.http.encodeURLPath
import io.ktor.http.takeFrom
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.Serializable
import kotlinx.serialization.StringFormat
import kotlinx.serialization.json.Json

const val KSRPC_BINARY: String = "KSRPC_BINARY"

enum class KsrpcType {
    EXE,
    SOCKET,
    HTTP,
    WEBSOCKET,
    LOCAL
}

@Serializable
data class KsrpcUri(
    val type: KsrpcType,
    val path: String,
) {
    override fun toString(): String {
        return path
    }
}

fun String.toKsrpcUri(): KsrpcUri = when {
    startsWith("http://") -> KsrpcUri(KsrpcType.HTTP, this)
    startsWith("https://") -> KsrpcUri(KsrpcType.HTTP, this)
    startsWith("ksrpc://") -> KsrpcUri(KsrpcType.SOCKET, this)
    startsWith("ws://") -> KsrpcUri(KsrpcType.WEBSOCKET, this)
    startsWith("local://") -> KsrpcUri(KsrpcType.LOCAL, this.substring("local://".length))
    else -> throw IllegalArgumentException("Unable to parse $this")
}

expect suspend fun KsrpcUri.connect(
    clientFactory: () -> HttpClient = { HttpClient { } },
): SerializedChannel

fun HttpClient.asChannel(baseUrl: String): SerializedChannel {
    val baseStripped = baseUrl.trimEnd('/')
    val client = this
    return object : SerializedChannel {
        override val serialization: StringFormat
            get() = Json

        override suspend fun call(endpoint: String, input: CallData): CallData {
            val response = client.post<HttpResponse>(
                "$baseStripped/call/${endpoint.encodeURLPath()}"
            ) {
                accept(ContentType.Application.Json)
                headers[KSRPC_BINARY] = input.isBinary.toString()
                body = if (input.isBinary) input.readBinary() else input.readSerialized()
            }
            response.checkErrors()
            if (response.headers[KSRPC_BINARY]?.toBoolean() == true) {
                return CallData.create(response.content)
            }
            return CallData.create(response.content.readRemaining().readText())
        }

        override suspend fun close() {
        }
    }
}

private suspend fun HttpResponse.checkErrors() {
    if (status == HttpStatusCode.InternalServerError) {
        val text = receive<String>()
        if (text.startsWith(ERROR_PREFIX)) {
            throw Json.decodeFromString(
                RpcFailure.serializer(),
                text.substring(ERROR_PREFIX.length)
            ).toException()
        } else {
            throw IllegalStateException("Can't parse error $this")
        }
    }
}

suspend fun HttpClient.asWebsocketChannel(baseUrl: String): SerializedChannel {
    val baseStripped = baseUrl.trimEnd('/')
    val lock = Mutex()
    val client = webSocketSession {
        url.takeFrom(baseStripped)
        url.protocol = URLProtocol.WS
    }
    return object : SerializedChannel {
        override val serialization: StringFormat
            get() = Json

        override suspend fun call(endpoint: String, input: CallData): CallData {
            lock.lock(null)
            var needsUnlock = true
            try {
                client.send(endpoint)
                client.send(input)
                return client.receiveCallData {
                    lock.unlock(null)
                    needsUnlock = false
                }
            } finally {
                if (needsUnlock) {
                    lock.unlock(null)
                }
            }
        }

        override suspend fun close() {
            client.close()
        }
    }
}
