package com.monkopedia.ksrpc

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

class ChannelContext(val channel: SerializedChannel) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*>
        get() = Key

    object Key : CoroutineContext.Key<ChannelContext>
}

suspend fun channel(): SerializedChannel? {
    return coroutineContext[ChannelContext.Key]?.channel
}

suspend fun host(): ChannelHost? {
    val channel = channel()
    return (channel as? ChannelHostProvider)?.host
}

suspend fun client(): ChannelClient? {
    val channel = channel()
    return (channel as? ChannelClientProvider)?.client
}