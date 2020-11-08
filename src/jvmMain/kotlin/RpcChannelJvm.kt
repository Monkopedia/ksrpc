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
import java.io.File
import java.net.Socket
import kotlin.reflect.full.companionObjectInstance
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

fun String.toKsrpcUri(): KsrpcUri = when {
    startsWith("http://") -> KsrpcUri(KsrpcType.HTTP, this)
    startsWith("ksrpc://") -> KsrpcUri(KsrpcType.SOCKET, this)
    startsWith("local://") -> KsrpcUri(KsrpcType.LOCAL, this.substring("local://".length))
    File(this).exists() -> {
        require(File(this).canExecute()) {
            "$this not executable"
        }
        KsrpcUri(KsrpcType.EXE, this)
    }
    else -> throw IllegalArgumentException("Unable to parse $this")
}

actual suspend fun KsrpcUri.connect(): SerializedChannel {
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
            val client = HttpClient()
            client.asChannel(path)
        }
        KsrpcType.LOCAL -> {
            val cls = Class.forName(path, true, this::class.java.classLoader)
            val companion = cls.findServiceObj() ?: error("Can't find RpcObject")
            val instance = (cls.newInstance() as RpcService)
            companion.channel(instance).serialized(companion)
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

fun <T : RpcService> RpcChannel.serialized(
    rpcObject: RpcObject<T>,
    errorListener: ErrorListener = ErrorListener { },
    json: Json = Json { isLenient = true }
): SerializedChannel {
    val rpcChannel = this
    return object : SerializedChannel {
        private var nextId = 0
        private val serviceMap by lazy {
            mutableMapOf<Int, SerializedChannel>()
        }
        override suspend fun call(str: String, input: String): String {
            return try {
                str.toIntOrNull()?.let { serviceId ->
                    serviceMap[serviceId]?.let { channel ->
                        val parsedJson = json.parseToJsonElement(input)
                        val endpoint = parsedJson.jsonObject["endpoint"]?.jsonPrimitive?.content
                            ?: throw IllegalArgumentException("Malformed input $input")
                        val subInput = parsedJson.jsonObject["input"].toString()
                        return@call channel.call(endpoint, subInput)
                    }
                }
                val rpcEndpoint = rpcObject.info.findEndpoint(str)
                    as RpcServiceInfoBase.RpcEndpoint<T, Any?, Any?>
                val (_, isService, inputSer, outputSer) = rpcEndpoint
                val output = rpcChannel.call(
                    str,
                    inputSer,
                    outputSer,
                    if (input.isNotEmpty()) json.decodeFromString(inputSer, input) else null
                )
                if (isService) {
                    val serviceId = ++nextId
                    val service = rpcEndpoint.subservice as RpcObject<RpcService>
                    serviceMap[serviceId] = service.channel(output as RpcService)
                        .serialized(service, errorListener, json)
                    json.encodeToString(String.serializer(), serviceId.toString())
                } else {
                    json.encodeToString(outputSer, output)
                }
            } catch (t: Throwable) {
                errorListener.onError(t)
                ERROR_PREFIX + json.encodeToString(RpcFailure.serializer(), RpcFailure(t.asString))
            }
        }
    }
}

fun <T : RpcService> RpcObject<T>.serializedChannel(
    service: T,
    errorListener: ErrorListener = ErrorListener { }
): SerializedChannel {
    return info.createChannelFor(service).serialized(this, errorListener = errorListener)
}
