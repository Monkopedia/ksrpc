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
package com.monkopedia.ksrpc.internal.jsonrpc

import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.RpcMethod
import com.monkopedia.ksrpc.channels.CallData
import com.monkopedia.ksrpc.channels.SerializedService
import kotlin.coroutines.CoroutineContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

internal class JsonRpcSerializedChannel(
    override val context: CoroutineContext,
    private val channel: JsonRpcChannel,
    override val env: KsrpcEnvironment
) : SerializedService {
    private val onCloseCallbacks = mutableSetOf<suspend () -> Unit>()
    private val json = (env.serialization as? Json) ?: Json

    override suspend fun call(endpoint: RpcMethod<*, *, *>, input: CallData): CallData {
        return call(endpoint.endpoint, input, !endpoint.hasReturnType)
    }

    override suspend fun call(endpoint: String, input: CallData): CallData {
        return call(endpoint, input, false)
    }

    private suspend fun call(endpoint: String, input: CallData, isNotify: Boolean): CallData {
        require(!input.isBinary) {
            "JsonRpc does not support binary data"
        }
        val message = json.decodeFromString<JsonElement?>(input.readSerialized())
        val response = channel.execute(endpoint, message, isNotify)

        return CallData.create(
            if (isNotify) json.encodeToString(Unit)
            else json.encodeToString(response)
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
