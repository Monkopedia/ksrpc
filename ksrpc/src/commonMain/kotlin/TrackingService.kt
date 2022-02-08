package com.monkopedia.ksrpc

import com.monkopedia.ksrpc.channels.SerializedService

/**
 * Tagged with internal for now until this has some more thorough testing.
 */
internal abstract class TrackingService : RpcService {
    private val serializations = mutableSetOf<SerializedService>()

    internal fun onSerializationCreated(serialization: SerializedService) {
        if (serializations.add(serialization) && serializations.size == 1) {
            onFirstClientOpened()
        }
    }

    internal suspend fun onSerializationClosed(serialization: SerializedService) {
        if (serializations.remove(serialization) && serializations.isEmpty()) {
            onAllClientsClosed()
        }
    }

    open fun onFirstClientOpened() = Unit
    abstract suspend fun onAllClientsClosed()
}