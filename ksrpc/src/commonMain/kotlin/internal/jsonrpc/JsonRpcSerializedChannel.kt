package com.monkopedia.ksrpc.internal.jsonrpc

import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.RpcMethod
import com.monkopedia.ksrpc.RpcObject
import com.monkopedia.ksrpc.RpcService
import com.monkopedia.ksrpc.channels.CallData
import com.monkopedia.ksrpc.channels.SerializedService
import com.monkopedia.ksrpc.channels.SuspendInit
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.coroutines.CoroutineContext

internal class JsonRpcSerializedChannel(
    override val context: CoroutineContext,
    private val channel: JsonRpcChannel,
    override val env: KsrpcEnvironment
) : SerializedService, SuspendInit {
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

