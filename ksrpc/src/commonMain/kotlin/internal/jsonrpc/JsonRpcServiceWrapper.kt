package com.monkopedia.ksrpc.internal.jsonrpc

import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.KsrpcEnvironment.Element
import com.monkopedia.ksrpc.asString
import com.monkopedia.ksrpc.channels.CallData
import com.monkopedia.ksrpc.channels.SerializedService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.coroutines.CoroutineContext

internal class JsonRpcServiceWrapper(
    private val channel: SerializedService,
) : JsonRpcChannel, Element by channel {
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

