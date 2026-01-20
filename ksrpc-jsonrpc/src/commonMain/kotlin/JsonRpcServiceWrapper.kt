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
import com.monkopedia.ksrpc.channels.CallData
import com.monkopedia.ksrpc.channels.SerializedService
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

class JsonRpcServiceWrapper(private val channel: SerializedService<String>) :
    JsonRpcChannel,
    Element<String> by channel {
    private val json = (channel.env.serialization as? Json) ?: Json
    override suspend fun execute(
        method: String,
        message: JsonElement?,
        isNotify: Boolean
    ): JsonElement? {
        val response = channel.call(method, CallData.create(json.encodeToString(message)))
        require(!response.isBinary) {
            "JsonRpc does not support binary data"
        }
        return json.decodeFromString(response.readSerialized())
    }

    override suspend fun close() {
        channel.close()
    }
}
