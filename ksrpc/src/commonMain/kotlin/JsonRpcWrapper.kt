package com.monkopedia.ksrpc

import com.monkopedia.ksrpc.JsonRpcError.Companion.INTERNAL_ERROR
import com.monkopedia.ksrpc.JsonRpcError.Companion.PARSE_ERROR
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.StringFormat
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.coroutines.coroutineContext

interface JsonRpcChannel {
    suspend fun execute(message: String): String?
}

class JsonRpcSerializedChannel(
    private val channel: JsonRpcChannel,
    override val serialization: Json
) : SerializedChannel {
    private var id = 1
    private val mutex = Mutex()

    override suspend fun call(endpoint: String, input: CallData): CallData {
        require(!input.isBinary) {
            "JsonRpc does not support binary data"
        }
        val message = serialization.decodeFromString<JsonElement>(input.readSerialized())
        mutex.withLock {
            val request = JsonRpcRequest(
                method = endpoint,
                params = message,
                id = id++
            )
            val response = channel.execute(serialization.encodeToString(request))
                ?: error("Unexpected null response from JsonRpcChannel")
            val parsedResponse = serialization.decodeFromString<JsonRpcResponse>(response)
            if (parsedResponse.error != null) {
                val error = parsedResponse.error.data?.let {
                    serialization.decodeFromJsonElement<RpcException>(it)
                } ?: IllegalStateException("JsonRpcError(${parsedResponse.error.code}): ${parsedResponse.error.message}")
                throw error
            }
            require (parsedResponse.id == id) {
                "Unexpected id ${parsedResponse.id} when executing non-batched jsonrpc $id"
            }
            return CallData.create(serialization.encodeToString(parsedResponse.result))
        }
    }

    override suspend fun close() {
    }
}

class JsonRpcWrapper(private val channel: SerializedChannel) : JsonRpcChannel {
    private val serialization = channel.serialization

    override suspend fun execute(message: String): String? {
        if (message.startsWith("[")) {
            return executeArray(message)
        }
        return try {
            val jsonRequest = serialization.decodeFromString<JsonRpcRequest>(message)
            serialization.encodeToString(execute(jsonRequest))
        } catch (t: IllegalArgumentException) {
            serialization.encodeToString(
                JsonRpcResponse(error = JsonRpcError(PARSE_ERROR, "Parse error"))
            )
        }
    }

    private suspend fun execute(message: JsonRpcRequest): JsonRpcResponse? {
        val id = message.id
        val response = channel
            .call(message.method, CallData.create(serialization.encodeToString(message.params)))
            .readSerialized()
        if (response.startsWith(ERROR_PREFIX)) {
            val errorStr = response.substring(ERROR_PREFIX.length)
            val error = serialization.decodeFromString(JsonElement.serializer(), errorStr)
            return JsonRpcResponse(
                id = id,
                error = JsonRpcError(code = INTERNAL_ERROR, "KSRPC Encoded error", data = error)
            )
        }
        val obj = serialization.decodeFromString<JsonElement>(response)
        return JsonRpcResponse(
            result = obj,
            id = id
        )
    }

    private suspend fun executeArray(message: String): String? {
        return try {
            val jsonRequest = serialization.decodeFromString<List<JsonRpcRequest>>(message)
            val scope = CoroutineScope(coroutineContext)

            val responses = jsonRequest.map {
                scope.async { execute(it) }
            }.awaitAll()
            serialization.encodeToString(responses)
        } catch (t: IllegalArgumentException) {
            serialization.encodeToString(
                JsonRpcResponse(error = JsonRpcError(PARSE_ERROR, "Parse error"))
            )
        }
    }
}

@Serializable
data class JsonRpcRequest(
    val method: String,
    val params: JsonElement,
    val id: Int?,
    val jsonrpc: String = "2.0"
)

@Serializable
data class JsonRpcResponse(
    val result: JsonElement? = null,
    val error: JsonRpcError? = null,
    val id: Int? = null,
    val jsonrpc: String = "2.0"
)

@Serializable
data class JsonRpcError(
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
