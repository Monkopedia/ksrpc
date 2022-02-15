package com.monkopedia.ksrpc.channels

import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.internal.ThreadSafeManager.threadSafe
import com.monkopedia.ksrpc.internal.jsonrpc.JsonRpcWriterBase
import com.monkopedia.ksrpc.internal.jsonrpc.jsonHeader
import com.monkopedia.ksrpc.internal.jsonrpc.jsonLine
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import kotlinx.coroutines.CoroutineScope


suspend fun Pair<ByteReadChannel, ByteWriteChannel>.asJsonRpcConnection(
    env: KsrpcEnvironment,
    includeContentHeaders: Boolean = true
): SingleChannelConnection {
    return threadSafe<SingleChannelConnection> { context ->
        JsonRpcWriterBase(
            CoroutineScope(context),
            context,
            env,
            if (includeContentHeaders) jsonHeader(env) else jsonLine(env)
        )
    }.also {
        (it as? SuspendInit)?.init()
    }
}
