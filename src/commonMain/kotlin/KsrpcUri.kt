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

import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.encodeURLPath
import kotlinx.serialization.Serializable

enum class KsrpcType {
    EXE,
    SOCKET,
    HTTP,
    LOCAL
}

@Serializable
data class KsrpcUri(
    val type: KsrpcType,
    val path: String
) {
    override fun toString(): String {
        return path
    }
}

expect suspend fun KsrpcUri.connect(): SerializedChannel

fun HttpClient.asChannel(baseUrl: String): SerializedChannel {
    val baseStripped = baseUrl.trimEnd('/')
    val client = this
    return object : SerializedChannel {
        override suspend fun call(str: String, input: String): String {
            return client.post("$baseStripped/${str.encodeURLPath()}") {
                accept(ContentType.Application.Json)
                body = input
            }
        }

        override suspend fun close() {
            client.close()
        }
    }
}
