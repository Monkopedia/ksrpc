/*
 * Copyright 2021 Jason Monk
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     https://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.monkopedia.ksrpc.internal

import com.monkopedia.ksrpc.KsrpcEnvironment
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
    inline fun <reified T : KsrpcEnvironment.Element> threadSafe(
        creator: (CoroutineContext) -> T
    ): T

    inline fun ThreadSafeKeyedConnection.threadSafeProvider(): ConnectionProvider
    inline fun ThreadSafeKeyedClient.threadSafeProvider(): ChannelClientProvider
    inline fun ThreadSafeKeyedHost.threadSafeProvider(): ChannelHostProvider
}
