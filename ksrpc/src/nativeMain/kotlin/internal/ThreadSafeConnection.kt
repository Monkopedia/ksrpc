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
import com.monkopedia.ksrpc.channels.ChannelHost
import com.monkopedia.ksrpc.channels.ChannelId
import com.monkopedia.ksrpc.channels.Connection
import com.monkopedia.ksrpc.channels.ConnectionInternal
import com.monkopedia.ksrpc.channels.ConnectionProvider
import com.monkopedia.ksrpc.channels.SerializedService
import com.monkopedia.ksrpc.channels.SuspendInit
import com.monkopedia.ksrpc.internal.ThreadSafeManager.threadSafe

internal class ThreadSafeConnection(
    threadSafe: ThreadSafe<Connection>,
    override val env: KsrpcEnvironment
) : ThreadSafeUser<Connection>(threadSafe), ConnectionInternal, SuspendInit {

    override suspend fun registerHost(service: SerializedService): ChannelId {
        val threadSafeService = service.threadSafe()
        return useSafe {
            it.registerHost(threadSafeService)
        }
    }

    override suspend fun registerDefault(service: SerializedService) {
        val threadSafeService = service.threadSafe()
        return useSafe {
            it.registerDefault(threadSafeService)
        }
    }

    override suspend fun close(id: ChannelId) {
        return useSafe {
            it.close(id)
        }
    }

    override suspend fun wrapChannel(channelId: ChannelId): SerializedService {
        return useSafe {
            it.wrapChannel(channelId).threadSafe()
        }
    }

    override suspend fun call(
        channelId: ChannelId,
        endpoint: String,
        data: CallData
    ): CallData {
        return useSafe {
            it.call(channelId, endpoint, data)
        }
    }
}

internal class ThreadSafeConnectionProvider(private val key: Any) : ConnectionProvider {
    override val host: ChannelHost?
        get() = (key.threadSafe() as ThreadSafe<Connection>).getWrapper<ConnectionInternal>().host
    override val client: ChannelClient?
        get() = (key.threadSafe() as ThreadSafe<Connection>).getWrapper<ConnectionInternal>().client
}
