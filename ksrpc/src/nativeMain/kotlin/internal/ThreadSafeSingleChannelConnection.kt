package com.monkopedia.ksrpc.internal

import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.channels.SerializedService
import com.monkopedia.ksrpc.channels.SingleChannelConnection
import com.monkopedia.ksrpc.channels.SuspendInit
import com.monkopedia.ksrpc.internal.ThreadSafeManager.threadSafe

internal class ThreadSafeSingleChannelConnection (
    threadSafe: ThreadSafe<SingleChannelConnection>,
    override val env: KsrpcEnvironment
) : ThreadSafeUser<SingleChannelConnection>(threadSafe), SingleChannelConnection, SuspendInit {

    override suspend fun registerDefault(service: SerializedService) {
        val threadSafeService = service.threadSafe()
        useSafe {
            it.registerDefault(threadSafeService)
        }
    }

    override suspend fun defaultChannel(): SerializedService {
        return useSafe {
            it.defaultChannel().threadSafe()
        }
    }
}