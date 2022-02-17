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
import com.monkopedia.ksrpc.channels.CallData
import com.monkopedia.ksrpc.channels.ChannelClient
import com.monkopedia.ksrpc.channels.ChannelClientProvider
import com.monkopedia.ksrpc.channels.ChannelId
import com.monkopedia.ksrpc.channels.SerializedService
import com.monkopedia.ksrpc.internal.ThreadSafeManager.threadSafe

internal class ThreadSafeChannelClient(
    threadSafe: ThreadSafe<ChannelClient>,
    override val env: KsrpcEnvironment
) : ThreadSafeUser<ChannelClient>(threadSafe), ChannelClient {

    override suspend fun wrapChannel(channelId: ChannelId): SerializedService {
        return useSafe {
            it.wrapChannel(channelId).threadSafe()
        }
    }

    override suspend fun call(channelId: ChannelId, endpoint: String, data: CallData): CallData {
        return useSafe {
            it.call(channelId, endpoint, data)
        }
    }

    override suspend fun close(id: ChannelId) {
        return useSafe {
            it.close(id)
        }
    }
}
internal class ThreadSafeClientProvider(private val key: Any) : ChannelClientProvider {
    override val client: ChannelClient?
        get() = (key.threadSafe() as? ChannelClientProvider)?.client
}
