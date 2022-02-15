package com.monkopedia.ksrpc.internal

import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.channels.ChannelHost
import com.monkopedia.ksrpc.channels.SuspendInit
import com.monkopedia.ksrpc.internal.jsonrpc.JsonRpcChannel
import kotlinx.serialization.json.JsonElement
import kotlin.coroutines.CoroutineContext
import kotlin.native.concurrent.DetachedObjectGraph

internal class ThreadSafeJsonRpcChannel(
    threadSafe: ThreadSafe<JsonRpcChannel>,
    override val env: KsrpcEnvironment
) : ThreadSafeUser<JsonRpcChannel>(threadSafe), JsonRpcChannel, SuspendInit {

    override suspend fun execute(
        method: String,
        message: JsonElement?,
        isNotify: Boolean
    ): JsonElement? {
        return useSafe {
            it.execute(method, message, isNotify)
        }
    }
}