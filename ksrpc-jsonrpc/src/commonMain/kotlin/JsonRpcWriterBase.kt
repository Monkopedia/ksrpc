/**
 * Copyright (C) 2025 Jason Monk <monkopedia@gmail.com>
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

import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.RpcException
import com.monkopedia.ksrpc.asString
import com.monkopedia.ksrpc.channels.SerializedService
import com.monkopedia.ksrpc.channels.SingleChannelConnection
import com.monkopedia.ksrpc.internal.MultiChannel
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

class JsonRpcWriterBase(
    private val scope: CoroutineScope,
    private val context: CoroutineContext,
    override val env: KsrpcEnvironment<String>,
    private val comm: JsonRpcTransformer
) : JsonRpcChannel, SingleChannelConnection<String> {
    private val json = (env.serialization as? Json) ?: Json

    private var baseChannel = CompletableDeferred<JsonRpcChannel>()
    private val multiChannel = MultiChannel<JsonRpcResponse>()

    init {
        scope.launch {
            withContext(context) {
                try {
                    while (comm.isOpen) {
                        val p = comm.receive() ?: continue
                        if ((p as? JsonObject)?.containsKey("method") == true) {
                            val request = json.decodeFromJsonElement<JsonRpcRequest>(p)
                            launchRequestHandler(baseChannel.await(), request)
                        } else {
                            val response = json.decodeFromJsonElement<JsonRpcResponse>(p)
                            multiChannel.send(response.id.toString(), response)
                        }
                    }
                } catch (t: Throwable) {
                    env.errorListener.onError(t)
                    try {
                        close()
                    } catch (t: Throwable) {
                    }
                }
            }
        }
    }

    private fun launchRequestHandler(channel: JsonRpcChannel, message: JsonRpcRequest) {
        scope.launch(context) {
            try {
                val response =
                    channel.execute(message.method, message.params, message.id == null)
                if (message.id == null) return@launch
                comm.send(
                    json.encodeToJsonElement(
                        JsonRpcResponse(
                            result = response,
                            id = message.id
                        )
                    )
                )
            } catch (t: Throwable) {
                env.errorListener.onError(t)
                if (message.id != null) {
                    comm.send(
                        json.encodeToJsonElement(
                            JsonRpcResponse(
                                error = JsonRpcError(JsonRpcError.INTERNAL_ERROR, t.asString),
                                id = message.id
                            )
                        )
                    )
                }
            }
        }
    }

    override suspend fun execute(
        method: String,
        message: JsonElement?,
        isNotify: Boolean
    ): JsonElement? {
        val (id, pending) = if (isNotify) null to null else multiChannel.allocateReceive()
        val request = JsonRpcRequest(
            method = method,
            params = message,
            id = JsonPrimitive(id)
        )
        comm.send(json.encodeToJsonElement(request))
        val response = pending?.await() ?: return null
        if (response.error != null) {
            val error = response.error.data?.let {
                json.decodeFromJsonElement<RpcException>(it)
            } ?: IllegalStateException(
                "JsonRpcError(${response.error.code}): ${response.error.message}"
            )
            throw error
        }
        return response.result
    }

    override suspend fun close() {
        try {
            comm.close(IllegalStateException("JsonRpcWriter is shutting down"))
        } catch (t: IllegalStateException) {
            // Sometimes expected
        }
        try {
            multiChannel.close()
        } catch (t: Throwable) {
            // Thats fine, just pending messages getting unhappy.
        }
    }

    override suspend fun registerDefault(service: SerializedService<String>) {
        baseChannel.complete(JsonRpcServiceWrapper(service))
    }

    override suspend fun defaultChannel(): SerializedService<String> {
        return JsonRpcSerializedChannel(context, this, env)
    }
}
