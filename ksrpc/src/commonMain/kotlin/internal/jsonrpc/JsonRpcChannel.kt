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

import com.monkopedia.ksrpc.KsrpcEnvironment.Element
import com.monkopedia.ksrpc.SuspendCloseable
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

internal interface JsonRpcChannel : SuspendCloseable, Element {
    suspend fun execute(method: String, message: JsonElement?, isNotify: Boolean): JsonElement?
}

@Serializable
internal data class JsonRpcRequest(
    @Required
    val jsonrpc: String = "2.0",
    val method: String,
    val params: JsonElement?,
    val id: JsonPrimitive? = null,
)

@Serializable
internal data class JsonRpcResponse(
    @Required
    val jsonrpc: String = "2.0",
    val result: JsonElement? = null,
    val error: JsonRpcError? = null,
    val id: JsonPrimitive? = null,
)

@Serializable
internal data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null
) {
    companion object {
        /**
         * Invalid JSON was received by the server.
         * An error occurred on the server while parsing the JSON text.
         */
        const val PARSE_ERROR = -32700

        /**
         * The JSON sent is not a valid Request object.
         */
        const val INVALID_REQUEST = -32600

        /**
         * The method does not exist / is not available.
         */
        const val METHOD_NOT_FOUND = -32601

        /**
         * Invalid method parameter(s).
         */
        const val INVALID_PARAMS = -32602

        /**
         * Internal JSON-RPC error.
         */
        const val INTERNAL_ERROR = -32603

        /**
         * Reserved for implementation-defined server-errors.
         */
        const val MIN_SERVER_ERROR = -32000

        /**
         * Reserved for implementation-defined server-errors.
         */
        const val MAX_SERVER_ERROR = -32099
    }
}
