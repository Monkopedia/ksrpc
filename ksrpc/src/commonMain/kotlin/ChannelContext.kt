package com.monkopedia.ksrpc

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

class HostChannelContext(val channel: ChannelHostProvider) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*>
        get() = Key

    object Key : CoroutineContext.Key<HostChannelContext>
}
class ClientChannelContext(val channel: ChannelClientProvider) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*>
        get() = Key

    object Key : CoroutineContext.Key<ClientChannelContext>
}

suspend fun host(): ChannelHost? {
    val channel = coroutineContext[HostChannelContext.Key]?.channel
    return channel?.host
}

suspend fun client(): ChannelClient? {
    val channel = coroutineContext[ClientChannelContext.Key]?.channel
    return channel?.client
}