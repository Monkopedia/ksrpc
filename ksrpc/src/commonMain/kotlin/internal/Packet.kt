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

import com.monkopedia.ksrpc.BidirectionalChannel
import com.monkopedia.ksrpc.CallData
import com.monkopedia.ksrpc.SerializedChannel
import com.monkopedia.ksrpc.SuspendCloseable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.StringFormat

internal data class Packet(
    val input: Boolean,
    val endpoint: String,
    val data: CallData
)

internal interface PacketExchanger : SuspendCloseable {
    suspend fun call(packet: Packet): Packet
}

internal interface PacketChannel : SuspendCloseable {
    suspend fun send(packet: Packet)
    suspend fun receive(): Packet
}

internal abstract class PacketChannelBase(
    override val serialization: StringFormat
) : PacketChannel, SerializedChannel, BidirectionalChannel {
    private var lock = Mutex()
    private var callLock = Mutex()

    abstract suspend fun receiveImpl(): Packet

    override suspend fun CoroutineScope.receivingChannel(): SerializedChannel {
        ensureInitBidi()
        return this@PacketChannelBase
    }

    private lateinit var receiveChannel: Channel<Packet>
    private val serviceCompletion by lazy {
        CompletableDeferred<SerializedChannel>()
    }
    private var isBidi = false

    private suspend fun CoroutineScope.ensureInitBidi() {
        if (isBidi) return
        lock.withLock {
            if (isBidi) return

            receiveChannel = Channel()
            launch {
                val service = serviceCompletion.await()
                try {
                    while (true) {
                        val p = receiveImpl()
                        if (p.input) {
                            launch {
                                service.handleInput(p)
                            }
                        } else {
                            receiveChannel.send(p)
                        }
                    }
                } catch (t: Throwable) {
                    receiveChannel.close(t)
                }
            }

            isBidi = true
        }
    }

    override suspend fun CoroutineScope.serve(sendingChannel: SerializedChannel) {
        ensureInitBidi()
        serviceCompletion.complete(sendingChannel)
    }

    private suspend fun SerializedChannel.handleInput(p: Packet) {
        val output = call(p.endpoint, p.data)
        send(Packet(false, p.endpoint, output))
    }

    override suspend fun receive(): Packet {
        lock.withLock {
            return if (isBidi) {
                receiveChannel.receive()
            } else {
                receiveImpl()
            }
        }
    }

    override suspend fun call(endpoint: String, input: CallData): CallData {
        callLock.withLock {
            send(Packet(true, endpoint, input))
            return receive().data
        }
    }

    override suspend fun close() {
        lock.withLock {
            if (isBidi) {
                receiveChannel.close()
            }
        }
    }
}

internal abstract class PacketExchangerBase(
    override val serialization: StringFormat
) : PacketExchanger, SerializedChannel {

    override suspend fun call(endpoint: String, input: CallData): CallData {
        return call(Packet(true, endpoint, input)).data
    }
}
