package com.monkopedia.ksrpc.internal

import com.monkopedia.ksrpc.CallData
import com.monkopedia.ksrpc.ChannelClient
import com.monkopedia.ksrpc.ChannelClientProvider
import com.monkopedia.ksrpc.ChannelHost
import com.monkopedia.ksrpc.ChannelHostProvider
import com.monkopedia.ksrpc.ChannelId
import com.monkopedia.ksrpc.Connection
import com.monkopedia.ksrpc.ConnectionProvider
import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.SerializedService
import com.monkopedia.ksrpc.internal.ThreadSafeManager.threadSafe
import kotlin.coroutines.CoroutineContext
import kotlin.native.concurrent.DetachedObjectGraph

internal class ThreadSafeConnection(
    context: CoroutineContext,
    reference: DetachedObjectGraph<Connection>,
    override val env: KsrpcEnvironment
) : ThreadSafe<Connection>(context, reference), Connection {

    override suspend fun init() {
        return useSafe {
            it.init()
        }
    }

    override suspend fun registerHost(service: SerializedService): ChannelId {
        val threadSafeService = service.threadSafe()
        return useSafe {
            it.registerHost(threadSafeService)
        }
    }

    override suspend fun registerDefault(service: SerializedService) {
        val threadSafeService = service.threadSafe()
        return useSafe {
            it.registerDefault(threadSafeService)
        }
    }

    override suspend fun close(id: ChannelId) {
        return useSafe {
            it.close(id)
        }
    }

    override suspend fun close() {
        return useSafe {
            it.close()
        }
    }

    override suspend fun wrapChannel(channelId: ChannelId): SerializedService {
        return useSafe {
            it.wrapChannel(channelId).threadSafe()
        }
    }

    override suspend fun call(
        channelId: ChannelId,
        endpoint: String,
        data: CallData
    ): CallData {
        return useSafe {
            it.call(channelId, endpoint, data)
        }
    }

    override suspend fun onClose(onClose: suspend () -> Unit) {
        return useSafe {
            it.onClose(onClose)
        }
    }
}

internal class ThreadSafeConnectionProvider(private val key: Any) : ConnectionProvider {
    override val host: ChannelHost?
        get() = (key.threadSafe() as? ChannelHostProvider)?.host
    override val client: ChannelClient?
        get() = (key.threadSafe() as? ChannelClientProvider)?.client
}
