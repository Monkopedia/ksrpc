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
