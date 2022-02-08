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
