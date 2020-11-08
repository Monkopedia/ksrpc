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
import io.ktor.client.engine.curl.Curl

fun String.toKsrpcUri(): KsrpcUri = when {
    startsWith("http://") -> KsrpcUri(KsrpcType.HTTP, this)
    startsWith("ksrpc://") -> KsrpcUri(KsrpcType.SOCKET, this)
    startsWith("local://") -> KsrpcUri(KsrpcType.LOCAL, this.substring("local://".length))
    else -> throw IllegalArgumentException("Unable to parse $this")
}

actual suspend fun KsrpcUri.connect(): SerializedChannel {
    return when (type) {
        KsrpcType.EXE -> {
            throw NotImplementedError("EXE not supported in JS")
        }
        KsrpcType.SOCKET -> {
            throw NotImplementedError("Socket not supported in JS")
        }
        KsrpcType.LOCAL -> {
            throw NotImplementedError("Local not supported in JS")
        }
        KsrpcType.HTTP -> {
            val client = HttpClient(Curl) {
                engine {
                }
            }
            client.asChannel(path)
        }
    }
}
