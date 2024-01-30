/**
 * Copyright (C) 2024 Jason Monk <monkopedia@gmail.com>
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
import com.monkopedia.ksrpc.channels.CallData
import com.monkopedia.ksrpc.channels.ChannelClient
import com.monkopedia.ksrpc.channels.ChannelId
import com.monkopedia.ksrpc.channels.SerializedService
import kotlin.coroutines.CoroutineContext

class SubserviceChannel<T>(
    private val baseChannel: ChannelClient<T>,
    private val serviceId: ChannelId
) : SerializedService<T> {

    private val onCloseCallbacks = mutableListOf<suspend () -> Unit>()
    override val env: KsrpcEnvironment<T>
        get() = baseChannel.env
    override val context: CoroutineContext
        get() = baseChannel.context

    override suspend fun call(endpoint: String, input: CallData<T>): CallData<T> {
        return baseChannel.call(serviceId, endpoint, input)
    }

    override suspend fun onClose(onClose: suspend () -> Unit) {
        onCloseCallbacks.add(onClose)
    }

    override suspend fun close() {
        baseChannel.close(serviceId)
        onCloseCallbacks.forEach { it.invoke() }
    }
}
