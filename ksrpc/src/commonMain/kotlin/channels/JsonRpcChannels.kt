package com.monkopedia.ksrpc.channels

import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.RpcObject
import com.monkopedia.ksrpc.RpcService
import com.monkopedia.ksrpc.internal.ThreadSafeManager.threadSafe
import com.monkopedia.ksrpc.internal.jsonrpc.JsonRpcRequest
import com.monkopedia.ksrpc.internal.jsonrpc.JsonRpcResponse
import com.monkopedia.ksrpc.internal.jsonrpc.JsonRpcSerializedChannel
import com.monkopedia.ksrpc.internal.jsonrpc.JsonRpcServiceWrapper
import com.monkopedia.ksrpc.internal.jsonrpc.JsonRpcTransformer
import com.monkopedia.ksrpc.internal.jsonrpc.JsonRpcWriterBase
import com.monkopedia.ksrpc.internal.jsonrpc.jsonHeaderReceiver
import com.monkopedia.ksrpc.internal.jsonrpc.jsonHeaderSender
import com.monkopedia.ksrpc.internal.jsonrpc.jsonLineReceiver
import com.monkopedia.ksrpc.internal.jsonrpc.jsonLineSender
import com.monkopedia.ksrpc.internal.jsonrpc.launchServeJsonMessages
import com.monkopedia.ksrpc.rpcObject
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.core.internal.DangerousInternalIoApi
import io.ktor.utils.io.preventFreeze
import kotlinx.coroutines.CoroutineScope

private class JsonServiceChannel(
    override val env: KsrpcEnvironment,
    private val comm: JsonRpcTransformer<JsonRpcResponse, JsonRpcRequest>
) : SingleServiceChannel {
    override suspend fun hostService(service: SerializedService) {
        threadSafe { context ->
            launchServeJsonMessages(
                CoroutineScope(context),
                context,
                JsonRpcServiceWrapper(service),
                env,
                comm
            )
        }
    }
}

fun Pair<ByteReadChannel, ByteWriteChannel>.asJsonRpcHost(
    env: KsrpcEnvironment,
    includeContentHeaders: Boolean = true
): SingleServiceChannel {
    return JsonServiceChannel(
        env,
        if (includeContentHeaders) jsonHeaderReceiver(env) else jsonLineReceiver(env)
    )
}

suspend inline fun <reified T : RpcService> Pair<ByteReadChannel, ByteWriteChannel>.asJsonRpcChannel(
    env: KsrpcEnvironment,
    includeContentHeaders: Boolean = true
): SerializedService {
    return asJsonRpcChannel(env, includeContentHeaders, rpcObject())
}

@OptIn(DangerousInternalIoApi::class)
suspend fun <T : RpcService> Pair<ByteReadChannel, ByteWriteChannel>.asJsonRpcChannel(
    env: KsrpcEnvironment,
    includeContentHeaders: Boolean = true,
    obj: RpcObject<T>
): SerializedService {
    return threadSafe<SerializedService> { context ->
        JsonRpcSerializedChannel(
            context,
            obj,
            JsonRpcWriterBase(
                CoroutineScope(context),
                context,
                env,
                if (includeContentHeaders) jsonHeaderSender(env) else jsonLineSender(env)
            ).also { it.preventFreeze() },
            env
        )
    }.also {
        (it as? SuspendInit)?.init()
    }
}
