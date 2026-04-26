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
package com.monkopedia.ksrpc.jsonrpc.internal

import com.monkopedia.ksrpc.KsrpcEnvironment.Element
import com.monkopedia.ksrpc.annotation.KsrpcInternal
import com.monkopedia.ksrpc.channels.CallData
import com.monkopedia.ksrpc.channels.SerializedService
import com.monkopedia.ksrpc.jsonrpc.JsonRpcCallId
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

@KsrpcInternal
class JsonRpcServiceWrapper(private val channel: SerializedService<String>) :
    JsonRpcChannel,
    Element<String> by channel {
    private val json = (channel.env.serialization as? Json) ?: Json
    override suspend fun execute(
        method: String,
        message: JsonElement?,
        isNotify: Boolean,
        id: JsonPrimitive?
    ): JsonElement? {
        // Forward the request id (if any) to the SerializedService call as a JsonRpcCallId.
        // RpcMethod.call then installs CurrentRpcCallElement for the handler — this is the
        // single central install site.
        val callId = id?.let { JsonRpcCallId(it) }
        val response = channel.call(
            method,
            CallData.create(json.encodeToString(message)),
            callId
        )
        if (response is com.monkopedia.ksrpc.channels.CallData.Error<String>) {
            // Native JSON-RPC error envelope: code/message/data map directly. Built-in
            // sentinels (-32601, -32603) collide deliberately with the JSON-RPC reserved
            // codes so vanilla JSON-RPC consumers see the right semantics without any
            // translation layer.
            val data = response.errorData?.let { json.decodeFromString<JsonElement>(it) }
            throw JsonRpcServerError(response.errorCode, response.errorMessage, data)
        }
        require(!response.isBinary) {
            "JsonRpc does not support binary data"
        }
        return json.decodeFromString(response.readSerialized())
    }

    override suspend fun close() {
        channel.close()
    }
}

/**
 * Internal control-flow exception carrying the wire-level JSON-RPC error envelope from
 * [JsonRpcServiceWrapper.execute] up to [JsonRpcWriterBase]'s request handler, which then
 * encodes it as the `error` field of a [JsonRpcResponse]. Not exposed to user code.
 */
@KsrpcInternal
class JsonRpcServerError(
    val errorCode: Int,
    override val message: String,
    val data: JsonElement?
) : RuntimeException(message)
