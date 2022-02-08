package com.monkopedia.ksrpc.internal

import com.monkopedia.ksrpc.channels.CallData
import com.monkopedia.ksrpc.channels.ChannelClient
import com.monkopedia.ksrpc.channels.ChannelClientProvider
import com.monkopedia.ksrpc.channels.ChannelHost
import com.monkopedia.ksrpc.channels.ChannelHostProvider
import com.monkopedia.ksrpc.channels.ChannelId
import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.channels.SerializedService
import com.monkopedia.ksrpc.internal.ThreadSafeManager.threadSafe
import kotlin.coroutines.CoroutineContext
import kotlin.native.concurrent.DetachedObjectGraph

internal class ThreadSafeChannelHost(
    context: CoroutineContext,
    reference: DetachedObjectGraph<ChannelHost>,
    override val env: KsrpcEnvironment
) : ThreadSafe<ChannelHost>(context, reference), ChannelHost {
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

    override suspend fun call(
        channelId: ChannelId,
        endpoint: String,
        data: CallData
    ): CallData {
        return useSafe {
            it.call(channelId, endpoint, data)
        }
    }

    override suspend fun close() {
        return useSafe {
            it.close()
        }
    }

    override suspend fun onClose(onClose: suspend () -> Unit) {
        return useSafe {
            it.onClose(onClose)
        }
    }
}
internal class ThreadSafeHostProvider(private val key: Any) : ChannelHostProvider {
    override val host: ChannelHost?
        get() = (key.threadSafe() as? ChannelHostProvider)?.host
}

