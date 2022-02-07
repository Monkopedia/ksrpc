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

import com.monkopedia.ksrpc.CallData
import com.monkopedia.ksrpc.ChannelHost
import com.monkopedia.ksrpc.ChannelHostProvider
import com.monkopedia.ksrpc.ChannelId
import com.monkopedia.ksrpc.ClientChannelContext
import com.monkopedia.ksrpc.Connection
import com.monkopedia.ksrpc.HostChannelContext
import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.SerializedService
import com.monkopedia.ksrpc.SuspendCloseable
import com.monkopedia.ksrpc.internal.ThreadSafeManager.createKey
import com.monkopedia.ksrpc.internal.ThreadSafeManager.threadSafe
import com.monkopedia.ksrpc.internal.ThreadSafeManager.threadSafeProvider
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class Packet(
    val input: Boolean,
    val id: String,
    val endpoint: String,
    val data: CallData
)

internal interface PacketChannel : SuspendCloseable {
    suspend fun send(packet: Packet)
    suspend fun receive(): Packet
}

internal abstract class PacketChannelBase(
    private val scope: CoroutineScope,
    context: CoroutineContext,
    override val env: KsrpcEnvironment
) : PacketChannel, Connection, ChannelHost, ThreadSafeKeyedConnection {
    private var isClosed = false
    private var callLock = Mutex()
    override val key: Any = createKey()

    private val threadSafeProvider = threadSafeProvider()

    @Suppress("LeakingThis")
    override val context: CoroutineContext =
        context + ClientChannelContext(threadSafeProvider) + HostChannelContext(threadSafeProvider)

    private val serviceChannel by lazy {
        HostSerializedChannelImpl(env, this.context).threadSafe<ChannelHost>()
    }
    private val onCloseObservers = mutableSetOf<suspend () -> Unit>()

    private var receiveChannel: Channel<Packet> = Channel()

    override suspend fun init() {
        scope.launch {
            withContext(context) {
                val serviceChannel = serviceChannel
                try {
                    while (true) {
                        val p = receive()
                        if (p.input) {
                            launch {
                                val response = callLock.withLock {
                                    withContext(context) {
                                        serviceChannel.call(
                                            ChannelId(p.id),
                                            p.endpoint,
                                            p.data
                                        )
                                    }
                                }
                                send(Packet(false, p.id, p.endpoint, response))
                            }
                        } else {
                            receiveChannel.send(p)
                        }
                    }
                } catch (t: Throwable) {
                    receiveChannel.close(t)
                }
            }
        }
    }

    override suspend fun wrapChannel(channelId: ChannelId): SerializedService {
        return SubserviceChannel(this, channelId)
    }

    override suspend fun call(channelId: ChannelId, endpoint: String, data: CallData): CallData {
        send(Packet(true, channelId.id, endpoint, data))
        return receiveChannel.receive().data
    }

    override suspend fun close(id: ChannelId) {
        val serviceChannel = serviceChannel
        serviceChannel.close(id)
        call(id, "", CallData.create("{}"))
    }

    override suspend fun registerDefault(service: SerializedService) {
        val serviceChannel = serviceChannel
        serviceChannel.registerDefault(service)
    }

    override suspend fun registerHost(service: SerializedService): ChannelId {
        val serviceChannel = serviceChannel
        return serviceChannel.registerHost(service)
    }

    override suspend fun close() {
        callLock.withLock {
            if (isClosed) return
            receiveChannel.close()
            serviceChannel.close()
            isClosed = true
            onCloseObservers.forEach { it.invoke() }
        }
    }

    override suspend fun onClose(onClose: suspend () -> Unit) {
        onCloseObservers.add(onClose)
    }
}
