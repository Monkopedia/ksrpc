package com.monkopedia.ksrpc.internal

import com.monkopedia.ksrpc.channels.ChannelClient
import com.monkopedia.ksrpc.channels.ChannelClientProvider
import com.monkopedia.ksrpc.channels.ChannelHost
import com.monkopedia.ksrpc.channels.ChannelHostProvider
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

internal class HostChannelContext(val channel: ChannelHostProvider) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*>
        get() = Key

    object Key : CoroutineContext.Key<HostChannelContext>
}
internal class ClientChannelContext(val channel: ChannelClientProvider) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*>
        get() = Key

    object Key : CoroutineContext.Key<ClientChannelContext>
}

internal suspend fun host(): ChannelHost? {
    val channel = coroutineContext[HostChannelContext.Key]?.channel
    return channel?.host
}

internal suspend fun client(): ChannelClient? {
    val channel = coroutineContext[ClientChannelContext.Key]?.channel
    return channel?.client
}