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
import com.monkopedia.ksrpc.ChannelContext
import com.monkopedia.ksrpc.ChannelHost
import com.monkopedia.ksrpc.ChannelId
import com.monkopedia.ksrpc.Connection
import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.SerializedService
import com.monkopedia.ksrpc.SuspendCloseable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

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
    scope: CoroutineScope,
    override val env: KsrpcEnvironment
) : PacketChannel, Connection, ChannelHost {
    private var isClosed = false
    private var callLock = Mutex()

    @Suppress("LeakingThis")
    override val context: CoroutineContext = ChannelContext(this)
    private val serviceChannel: HostSerializedChannelImpl =
        HostSerializedChannelImpl(env, context)
    private val onCloseObservers = mutableSetOf<suspend () -> Unit>()

    private var receiveChannel: Channel<Packet> = Channel()

    init {
        scope.launch(context) {
            try {
                while (true) {
                    val p = receive()
                    if (p.input) {
                        launch(context) {
                            val response = callLock.withLock {
                                serviceChannel.call(
                                    ChannelId(p.id),
                                    p.endpoint,
                                    p.data
                                )
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

    override suspend fun wrapChannel(channelId: ChannelId): SerializedService {
        return SubserviceChannel(this, channelId)
    }

    override suspend fun call(channelId: ChannelId, endpoint: String, data: CallData): CallData {
        send(Packet(true, channelId.id, endpoint, data))
        return receiveChannel.receive().data
    }

    override suspend fun close(id: ChannelId) {
        withContext(context) {
            serviceChannel.close(id)
        }
        call(id, "", CallData.create("{}"))
    }

    override suspend fun registerDefault(service: SerializedService) =
        withContext(context) {
            serviceChannel.registerDefault(service)
        }

    override suspend fun registerHost(service: SerializedService): ChannelId =
        withContext(context) {
            serviceChannel.registerHost(service)
        }

    override suspend fun close() {
        callLock.withLock {
            if (isClosed) return
            receiveChannel.close()
            withContext(context) {
                serviceChannel.close()
            }
            isClosed = true
            onCloseObservers.forEach { it.invoke() }
        }
    }

    fun onClose(onClose: suspend () -> Unit) {
        onCloseObservers.add(onClose)
    }
}
