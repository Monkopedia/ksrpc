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
import java.io.File
import java.net.Socket
import kotlin.reflect.full.companionObjectInstance

actual suspend fun KsrpcUri.connect(clientFactory: () -> HttpClient): SerializedChannel {
    return when (type) {
        KsrpcType.EXE -> {
            ProcessBuilder(listOf(path))
                .redirectError(File("/tmp/ksrpc_errors.txt"))
                .asChannel()
        }
        KsrpcType.SOCKET -> {
            val (host, port) = path.substring("ksrpc://".length).split(":")
            val socket = Socket(host, port.toInt())
            (socket.getInputStream() to socket.getOutputStream()).asChannel()
        }
        KsrpcType.HTTP -> {
            val client = clientFactory()
            client.asChannel(path)
        }
        KsrpcType.LOCAL -> {
            val cls = Class.forName(path, true, this::class.java.classLoader)
            val companion = cls.findServiceObj() ?: error("Can't find RpcObject")
            val instance = (cls.newInstance() as RpcService)
            companion.serializedChannel(instance)
        }
        KsrpcType.WEBSOCKET -> {
            val client = clientFactory()
            client.asWebsocketChannel(path)
        }
        KsrpcType.WEBSOCKET -> {
            val client = clientFactory()
            client.asWebsocketChannel(path)
        }
    }
}

private fun Class<*>.findServiceObj(): RpcObject<RpcService>? {
    if (kotlin.companionObjectInstance is RpcObject<*>) {
        return kotlin.companionObjectInstance as RpcObject<RpcService>
    }
    superclass?.findServiceObj()?.let { return it }
    interfaces?.forEach { int ->
        int.findServiceObj()?.let { return it }
    }
    return null
}