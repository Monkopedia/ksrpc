package com.monkopedia.ksrpc.internal

import com.monkopedia.ksrpc.CallData
import com.monkopedia.ksrpc.ChannelClient
import com.monkopedia.ksrpc.ChannelId
import com.monkopedia.ksrpc.SerializedService
import kotlinx.serialization.StringFormat

internal class ChannelClientImpl(
    private val connectionHandler: ChannelClient,
    private val id: ChannelId
) : SerializedService, ChannelClient by connectionHandler {
    override val serialization: StringFormat
        get() = connectionHandler.serialization

    override suspend fun call(endpoint: String, input: CallData): CallData {
        return connectionHandler.call(id, endpoint, input)
    }

    override fun wrapChannel(channelId: ChannelId): SerializedService {
        return ChannelClientImpl(connectionHandler, channelId)
    }

    override suspend fun close() {
        connectionHandler.close(id)
    }
}
