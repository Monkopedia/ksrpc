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

actual suspend fun KsrpcUri.connect(env: KsrpcEnvironment, clientFactory: () -> HttpClient): ChannelClient {
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
            clientFactory().asChannel(path, env)
        }
        KsrpcType.WEBSOCKET -> {
            clientFactory().asWebsocketChannel(path, env)
        }
    }
}
