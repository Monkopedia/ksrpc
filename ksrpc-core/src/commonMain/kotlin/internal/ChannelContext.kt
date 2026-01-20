/*
 * Copyright (C) 2026 Jason Monk <monkopedia@gmail.com>
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
import com.monkopedia.ksrpc.channels.ChannelHost
import com.monkopedia.ksrpc.internal.ClientChannelContext.Key
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

class HostChannelContext<T>(val channel: ChannelHost<T>) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*>
        get() = Key

    object Key : CoroutineContext.Key<HostChannelContext<*>>
}

class ClientChannelContext<T>(val channel: ChannelClient<T>) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*>
        get() = Key

    object Key : CoroutineContext.Key<ClientChannelContext<*>>
}

internal suspend fun <T> host(): ChannelHost<T>? {
    @Suppress("UNCHECKED_CAST")
    return coroutineContext[HostChannelContext.Key]?.channel as ChannelHost<T>?
}

internal suspend fun <T> client(): ChannelClient<T>? {
    @Suppress("UNCHECKED_CAST")
    return coroutineContext[Key]?.channel as ChannelClient<T>?
}
