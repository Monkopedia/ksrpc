package com.monkopedia.ksrpc.internal

import com.monkopedia.ksrpc.channels.ChannelClientInternal
import com.monkopedia.ksrpc.channels.ChannelClientProvider
import com.monkopedia.ksrpc.channels.ChannelHostInternal
import com.monkopedia.ksrpc.channels.ChannelHostProvider
import com.monkopedia.ksrpc.channels.ConnectionInternal
import com.monkopedia.ksrpc.channels.ConnectionProvider
import kotlin.coroutines.CoroutineContext

/**
 * Interface to specify key for threadsafe lookup to avoid construction circular dependency.
 */
internal interface ThreadSafeKeyed {
    val key: Any
}

internal interface ThreadSafeKeyedConnection : ThreadSafeKeyed, ConnectionInternal
internal interface ThreadSafeKeyedClient : ThreadSafeKeyed, ChannelClientInternal
internal interface ThreadSafeKeyedHost : ThreadSafeKeyed, ChannelHostInternal

internal expect object ThreadSafeManager {
    inline fun createKey(): Any
    inline fun <reified T : Any> T.threadSafe(): T
    inline fun <reified T : Any> threadSafe(creator: (CoroutineContext) -> T): T

    inline fun ThreadSafeKeyedConnection.threadSafeProvider(): ConnectionProvider
    inline fun ThreadSafeKeyedClient.threadSafeProvider(): ChannelClientProvider
    inline fun ThreadSafeKeyedHost.threadSafeProvider(): ChannelHostProvider
}
