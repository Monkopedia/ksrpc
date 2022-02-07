package com.monkopedia.ksrpc.internal

import com.monkopedia.ksrpc.ChannelClientProvider
import com.monkopedia.ksrpc.ChannelHostProvider
import com.monkopedia.ksrpc.Connection
import com.monkopedia.ksrpc.ConnectionProvider
import kotlin.coroutines.CoroutineContext

/**
 * Interface to specify key for threadsafe lookup to avoid construction circular dependency.
 */
interface ThreadSafeKeyed {
    val key: Any
}

interface ThreadSafeKeyedConnection : ThreadSafeKeyed, Connection
interface ThreadSafeKeyedClient : ThreadSafeKeyed, ChannelClientProvider
interface ThreadSafeKeyedHost : ThreadSafeKeyed, ChannelHostProvider

internal expect object ThreadSafeManager {
    inline fun createKey(): Any
    inline fun <reified T : Any> T.threadSafe(): T
    inline fun <reified T : Any> threadSafe(creator: (CoroutineContext) -> T): T
    inline fun ThreadSafeKeyedConnection.threadSafeProvider(): ConnectionProvider
    inline fun ThreadSafeKeyedClient.threadSafeProvider(): ChannelClientProvider
    inline fun ThreadSafeKeyedHost.threadSafeProvider(): ChannelHostProvider
}
