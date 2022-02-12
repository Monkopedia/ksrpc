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

internal suspend fun launchServeJsonMessages(
    scope: CoroutineScope,
    context: CoroutineContext,
    channel: JsonRpcChannel,
    env: KsrpcEnvironment,
    comm: JsonRpcTransformer<JsonRpcResponse, JsonRpcRequest>,
) {
    scope.launch(context) {
        while (true) {
            val message = comm.receive() ?: continue
            scope.launch(context) {
                try {
                    val response =
                        channel.execute(message.method, message.params, message.id == null)
                    if (message.id == null) return@launch
                    comm.send(JsonRpcResponse(result = response, id = message.id))
                } catch (t: Throwable) {
                    env.errorListener.onError(t)
                    if (message.id != null) {
                        comm.send(
                            JsonRpcResponse(
                                error = JsonRpcError(JsonRpcError.INTERNAL_ERROR, t.asString),
                                id = message.id
                            )
                        )
                    }
                }
            }
        }
    }
}