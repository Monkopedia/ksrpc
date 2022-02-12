package com.monkopedia.ksrpc.internal

import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.channels.SuspendInit
import com.monkopedia.ksrpc.internal.jsonrpc.JsonRpcChannel
import kotlinx.serialization.json.JsonElement
import kotlin.coroutines.CoroutineContext
import kotlin.native.concurrent.DetachedObjectGraph

internal class ThreadSafeJsonRpcChannel(
    context: CoroutineContext,
    reference: DetachedObjectGraph<JsonRpcChannel>,
    override val env: KsrpcEnvironment
) : ThreadSafe<JsonRpcChannel>(context, reference), JsonRpcChannel, SuspendInit {

    override suspend fun init() {
        return useSafe {
            (it as? SuspendInit)?.init()
        }
    }
    override suspend fun execute(
        method: String,
        message: JsonElement?,
        isNotify: Boolean
    ): JsonElement? {
        return useSafe {
            it.execute(method, message, isNotify)
        }
    }

    override suspend fun close() {
        return useSafe {
            it.close()
        }
    }
}