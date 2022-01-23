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
import com.monkopedia.ksrpc.ErrorListener
import com.monkopedia.ksrpc.SerializedService
import com.monkopedia.ksrpc.SuspendCloseable
import kotlinx.coroutines.CloseableCoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.StringFormat
import kotlin.native.concurrent.ThreadLocal

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
    errorListener: ErrorListener,
    override val serialization: StringFormat,
    private val channelThread: CloseableCoroutineDispatcher
) : PacketChannel, Connection, ChannelHost {
    private var isClosed = false
    private var callLock = Mutex()

    @Suppress("LeakingThis")
    private val channelContext = ChannelContext(this)
    @ThreadLocal
    private val serviceChannel: HostSerializedChannelImpl =
        HostSerializedChannelImpl(errorListener, serialization, channelThread, channelContext)

    private var receiveChannel: Channel<Packet> = Channel()

    init {
        scope.launch(channelThread) {
            coroutineContext[Job]?.invokeOnCompletion {
                scope.launch {
                    close()
                }
            }
            try {
                while (true) {
                    val p = receive()
                    if (p.input) {
                        launch(channelThread) {
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

    override fun wrapChannel(channelId: ChannelId): SerializedService {
        return SubserviceChannel(this, channelId)
    }

    override suspend fun call(channelId: ChannelId, endpoint: String, data: CallData): CallData {
        send(Packet(true, channelId.id, endpoint, data))
        return receiveChannel.receive().data
    }

    override suspend fun close(id: ChannelId) {
        withContext(channelThread) {
            serviceChannel.close(id)
        }
        call(id, "", CallData.create("{}"))
    }

    override suspend fun registerDefault(service: SerializedService) =
        withContext(channelThread) {
            serviceChannel.registerDefault(service)
        }

    override suspend fun registerHost(service: SerializedService): ChannelId =
        withContext(channelThread) {
            serviceChannel.registerHost(service)
        }

    override suspend fun close() {
        callLock.withLock {
            if (isClosed) return
            receiveChannel.close()
            withContext(channelThread) {
                serviceChannel.close()
            }
            isClosed = true
        }
    }
}
