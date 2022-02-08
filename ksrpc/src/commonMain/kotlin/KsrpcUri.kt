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

import com.monkopedia.ksrpc.channels.ChannelClient
import io.ktor.client.HttpClient
import kotlinx.serialization.Serializable

const val KSRPC_BINARY: String = "KSRPC_BINARY"
const val KSRPC_CHANNEL: String = "KSRPC_CHANNEL"

enum class KsrpcType {
    EXE,
    SOCKET,
    HTTP,
    WEBSOCKET,
    LOCAL
}

/**
 * Class with explicit specification of the type of connection used in a uri.
 *
 * Generally created using [String.toKsrpcUri].
 */
@Serializable
data class KsrpcUri(
    val type: KsrpcType,
    val path: String,
) {
    override fun toString(): String {
        return path
    }
}

/**
 * Parses all supported types of uris into explicitly specified [KsrpcUri].
 *
 * Note that not all types returned by this are supported by all platforms. Checking for
 * support needs to be handled elsewhere.
 */
fun String.toKsrpcUri(): KsrpcUri = when {
    startsWith("http://") -> KsrpcUri(KsrpcType.HTTP, this)
    startsWith("https://") -> KsrpcUri(KsrpcType.HTTP, this)
    startsWith("ksrpc://") -> KsrpcUri(KsrpcType.SOCKET, this)
    startsWith("ws://") -> KsrpcUri(KsrpcType.WEBSOCKET, this)
    startsWith("local://") -> KsrpcUri(KsrpcType.LOCAL, this.substring("local://".length))
    else -> throw IllegalArgumentException("Unable to parse $this")
}

/**
 * Creates a [ChannelClient] from a [KsrpcUri].
 * @Throws [IllegalArgumentException] if this [KsrpcType] is not supported on the current platform.
 */
expect suspend fun KsrpcUri.connect(
    env: KsrpcEnvironment,
    clientFactory: () -> HttpClient = { HttpClient { } },
): ChannelClient
