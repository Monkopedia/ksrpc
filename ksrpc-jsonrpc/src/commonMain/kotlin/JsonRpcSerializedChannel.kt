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

import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.RpcMethod
import com.monkopedia.ksrpc.annotation.KsrpcInternal
import com.monkopedia.ksrpc.channels.CallData
import com.monkopedia.ksrpc.channels.RpcCallId
import com.monkopedia.ksrpc.channels.SerializedService
import kotlin.coroutines.CoroutineContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

private val RpcMethod<*, *, *>.isNotification: Boolean
    get() = metadata("com.monkopedia.ksrpc.annotation.KsNotification") != null

@KsrpcInternal
class JsonRpcSerializedChannel(
    override val context: CoroutineContext,
    private val channel: JsonRpcChannel,
    override val env: KsrpcEnvironment<String>
) : SerializedService<String> {
    private val onCloseCallbacks = mutableSetOf<suspend () -> Unit>()
    private val json = (env.serialization as? Json) ?: Json

    override suspend fun call(
        endpoint: RpcMethod<*, *, *>,
        input: CallData<String>,
        callId: RpcCallId?
    ): CallData<String> = call(endpoint.endpoint, input, endpoint.isNotification)

    override suspend fun call(
        endpoint: String,
        input: CallData<String>,
        callId: RpcCallId?
    ): CallData<String> = call(endpoint, input, false)

    private suspend fun call(
        endpoint: String,
        input: CallData<String>,
        isNotify: Boolean
    ): CallData<String> {
        require(!input.isBinary) {
            "JsonRpc does not support binary data"
        }
        val message = json.decodeFromString<JsonElement?>(input.readSerialized())
        // Outbound stub path: no server-side id to forward. The wire id (if any) is allocated
        // by the underlying JsonRpcChannel.
        val response = try {
            channel.execute(endpoint, message, isNotify, id = null)
        } catch (e: JsonRpcServerError) {
            // Translate the native JSON-RPC error envelope back into a CallData.Error so the
            // routing layer (RpcMethod.decodeError) can rebuild a typed Throwable using the
            // method's @KsError forwardErrorMap.
            return CallData.Error(
                errorCode = e.errorCode,
                errorMessage = e.message,
                errorData = e.data?.let { json.encodeToString(JsonElement.serializer(), it) }
            )
        }

        return CallData.create(
            if (isNotify) {
                json.encodeToString(Unit)
            } else {
                json.encodeToString(response)
            }
        )
    }

    override suspend fun close() {
        channel.close()
        onCloseCallbacks.forEach {
            it.invoke()
        }
    }

    override suspend fun onClose(onClose: suspend () -> Unit) {
        onCloseCallbacks.add(onClose)
    }
}
