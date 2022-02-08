package com.monkopedia.ksrpc.internal

import com.monkopedia.ksrpc.channels.ChannelClientProvider
import com.monkopedia.ksrpc.channels.ChannelHostProvider
import com.monkopedia.ksrpc.channels.ConnectionProvider
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

internal actual object ThreadSafeManager {
    actual inline fun <reified T : Any> T.threadSafe(): T = this
    actual inline fun <reified T : Any> threadSafe(creator: (CoroutineContext) -> T): T =
        creator(EmptyCoroutineContext)

    actual inline fun createKey(): Any = Unit
    actual inline fun ThreadSafeKeyedConnection.threadSafeProvider(): ConnectionProvider = this
    actual inline fun ThreadSafeKeyedClient.threadSafeProvider(): ChannelClientProvider = this
    actual inline fun ThreadSafeKeyedHost.threadSafeProvider(): ChannelHostProvider = this
}
