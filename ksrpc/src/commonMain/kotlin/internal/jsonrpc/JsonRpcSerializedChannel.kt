package com.monkopedia.ksrpc.internal.jsonrpc

import com.monkopedia.ksrpc.KsrpcEnvironment
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

internal class JsonRpcSerializedChannel<T : RpcService>(
    override val context: CoroutineContext,
    private val service: RpcObject<T>,
    private val channel: JsonRpcChannel,
    override val env: KsrpcEnvironment
) : SerializedService, SuspendInit {
    private val onCloseCallbacks = mutableSetOf<suspend () -> Unit>()
    private val json = (env.serialization as? Json) ?: Json

    override suspend fun init() {
        super.init()
        (channel as? SuspendInit)?.init()
    }

    override suspend fun call(endpoint: String, input: CallData): CallData {
        require(!input.isBinary) {
            "JsonRpc does not support binary data"
        }
        val message = json.decodeFromString<JsonElement?>(input.readSerialized())
        val isNotify = isNotify(endpoint)
        val response = channel.execute(endpoint, message, isNotify)

        return CallData.create(
            if (isNotify) json.encodeToString(Unit)
            else json.encodeToString(response)
        )
    }

    private fun isNotify(endpointStr: String): Boolean {
        val endpoint = service.findEndpoint(endpointStr)
        return !endpoint.hasReturnType
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

