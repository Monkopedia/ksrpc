package com.monkopedia.ksrpc.internal

import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.channels.CallData
import com.monkopedia.ksrpc.channels.ChannelClient
import com.monkopedia.ksrpc.channels.ChannelClientProvider
import com.monkopedia.ksrpc.channels.ChannelId
import com.monkopedia.ksrpc.channels.SerializedService
import com.monkopedia.ksrpc.internal.ThreadSafeManager.threadSafe
import kotlin.coroutines.CoroutineContext
import kotlin.native.concurrent.DetachedObjectGraph

internal class ThreadSafeChannelClient(
    context: CoroutineContext,
    reference: DetachedObjectGraph<ChannelClient>,
    override val env: KsrpcEnvironment
) : ThreadSafe<ChannelClient>(context, reference), ChannelClient {

    override suspend fun wrapChannel(channelId: ChannelId): SerializedService {
        return useSafe {
            it.wrapChannel(channelId).threadSafe()
        }
    }

    override suspend fun call(channelId: ChannelId, endpoint: String, data: CallData): CallData {
        return useSafe {
            it.call(channelId, endpoint, data)
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

    override suspend fun onClose(onClose: suspend () -> Unit) {
        return useSafe {
            it.onClose(onClose)
        }
    }
}
internal class ThreadSafeClientProvider(private val key: Any) : ChannelClientProvider {
    override val client: ChannelClient?
        get() = (key.threadSafe() as? ChannelClientProvider)?.client
}
