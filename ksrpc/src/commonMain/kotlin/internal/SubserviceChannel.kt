package com.monkopedia.ksrpc.internal

import com.monkopedia.ksrpc.CallData
import com.monkopedia.ksrpc.SerializedChannel
import com.monkopedia.ksrpc.encodedEndpoint
import kotlinx.serialization.StringFormat

internal class SubserviceChannel(
    override val serialization: StringFormat,
    private val baseChannel: SerializedChannel,
    private val serviceId: String
) : SerializedChannel {

    override suspend fun call(endpoint: String, input: CallData): CallData {
        return call(listOf(endpoint), input)
    }

    private suspend fun call(
        target: List<String>,
        input: CallData
    ): CallData {
        if (baseChannel is SubserviceChannel) {
            return baseChannel.call(target + serviceId, input)
        }
        return baseChannel.call(serialization.encodedEndpoint(target + serviceId), input)
    }

    override suspend fun close() {
        call(listOf("", "close"), CallData.create(""))
    }
}