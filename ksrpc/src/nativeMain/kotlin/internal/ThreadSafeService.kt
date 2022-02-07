package com.monkopedia.ksrpc.internal

import com.monkopedia.ksrpc.CallData
import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.SerializedService
import kotlin.coroutines.CoroutineContext
import kotlin.native.concurrent.DetachedObjectGraph

internal class ThreadSafeService(
    context: CoroutineContext,
    reference: DetachedObjectGraph<SerializedService>,
    override val env: KsrpcEnvironment
) : ThreadSafe<SerializedService>(context, reference), SerializedService {

    override suspend fun call(endpoint: String, input: CallData): CallData {
        return useSafe {
            it.call(endpoint, input)
        }
    }

    override suspend fun close() {
        useSafe {
            it.close()
        }
    }

    override suspend fun onClose(onClose: suspend () -> Unit) {
        return useSafe {
            it.onClose(onClose)
        }
    }
}
