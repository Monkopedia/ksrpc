package com.monkopedia.ksrpc.internal.jsonrpc

import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.RpcException
import com.monkopedia.ksrpc.channels.SuspendInit
import io.ktor.utils.io.core.internal.DangerousInternalIoApi
import io.ktor.utils.io.preventFreeze
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.coroutines.CoroutineContext

@OptIn(DangerousInternalIoApi::class)
internal class JsonRpcWriterBase(
    private val scope: CoroutineScope,
    private val context: CoroutineContext,
    override val env: KsrpcEnvironment,
    private val comm: JsonRpcTransformer<JsonRpcRequest, JsonRpcResponse>,
) : JsonRpcChannel, SuspendInit {
    private val json = (env.serialization as? Json) ?: Json
    private var id = 1

    private val completions = mutableMapOf<Int, CompletableDeferred<JsonRpcResponse?>>()

    init {
        preventFreeze()
    }

    override suspend fun init() {
        scope.launch {
            withContext(context) {
                try {
                    while (comm.isOpen) {
                        val p = comm.receive() ?: continue
                        completions.remove(p.id)?.complete(p)
                            ?: println("Warning, no completion found for $p")
                    }
                } catch (t: Throwable) {
                    try {
                        close()
                    } catch (t: Throwable) {
                    }
                }
            }
        }
    }

    private fun allocateResponse(isNotify: Boolean): Pair<Deferred<JsonRpcResponse?>?, Int?> {
        if (isNotify) return null to null
        val id = id++
        return (CompletableDeferred<JsonRpcResponse?>() to id).also {
            completions[it.second] = it.first
        }
    }

    override suspend fun execute(
        method: String,
        message: JsonElement?,
        isNotify: Boolean
    ): JsonElement? {
        val (responseHolder, id) = allocateResponse(isNotify)
        val request = JsonRpcRequest(
            method = method,
            params = message,
            id = id
        )
        comm.send(request)
        val response = responseHolder?.await() ?: return null
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
    }
}