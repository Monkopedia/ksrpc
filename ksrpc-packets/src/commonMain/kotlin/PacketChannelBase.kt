/**
 * Copyright (C) 2022 Jason Monk <monkopedia@gmail.com>
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
package com.monkopedia.ksrpc.packets.internal

import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.channels.CallData
import com.monkopedia.ksrpc.channels.ChannelHost
import com.monkopedia.ksrpc.channels.ChannelId
import com.monkopedia.ksrpc.channels.Connection
import com.monkopedia.ksrpc.channels.SerializedService
import com.monkopedia.ksrpc.channels.randomUuid
import com.monkopedia.ksrpc.internal.ClientChannelContext
import com.monkopedia.ksrpc.internal.HostChannelContext
import com.monkopedia.ksrpc.internal.HostSerializedChannelImpl
import com.monkopedia.ksrpc.internal.MultiChannel
import com.monkopedia.ksrpc.internal.SubserviceChannel
import io.ktor.util.decodeBase64Bytes
import io.ktor.util.encodeBase64
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.close
import io.ktor.utils.io.core.readBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

private const val DEFAULT_MAX_SIZE = 16 * 1024L

abstract class PacketChannelBase(
    private val scope: CoroutineScope,
    final override val env: KsrpcEnvironment<String>
) : PacketChannel, Connection<String>, ChannelHost<String> {
    private var isClosed = false
    private var callLock = Mutex()
    protected open val maxSize: Long = DEFAULT_MAX_SIZE

    @Suppress("LeakingThis")
    override val context: CoroutineContext =
        ClientChannelContext(this) + HostChannelContext(this) + env.coroutineExceptionHandler

    private val serviceChannel by lazy {
        HostSerializedChannelImpl(env, this.context)
    }
    private val onCloseObservers = mutableSetOf<suspend () -> Unit>()

    private val binaryChannelLock = Mutex()
    private val binaryChannels = mutableMapOf<String, BinaryChannel>()

    private val multiChannel = MultiChannel<Packet>()

    init {
        scope.launch {
            withContext(context) {
                executeReceive(this)
            }
        }.also {
            onCloseObservers.add {
                it.cancel()
            }
        }
    }

    private suspend fun executeReceive(coroutineScope: CoroutineScope) {
        val serviceChannel = serviceChannel
        try {
            while (true) {
                val p = receive()
                coroutineScope.launch {
                    if (p.binary) {
                        val channel = getBinaryChannel(p.id)
                        binaryChannelLock.lock()
                        try {
                            channel.handlePacket(p)
                        } finally {
                            binaryChannelLock.unlock()
                        }
                        removeBinaryChannelIfDone(channel)
                    } else if (p.input) {
                        val callData = getCallData(p)
                        val response = withContext(context) {
                            serviceChannel.call(
                                ChannelId(p.id),
                                p.endpoint,
                                callData
                            )
                        }

                        sendPacket(false, p.id, p.messageId, p.endpoint, response)
                    } else {
                        multiChannel.send(p.messageId, p)
                    }
                }
            }
        } catch (t: Throwable) {
            binaryChannels.values.forEach { it.channel.close(t) }
            multiChannel.close(CancellationException("Multi-channel failure", t))
        }
    }

    private suspend fun CoroutineScope.sendPacket(
        input: Boolean,
        id: String,
        messageId: String,
        endpoint: String,
        response: CallData<String>
    ) {
        if (response.isBinary) {
            val binaryChannel = randomUuid()
            send(
                Packet(
                    input = input,
                    binary = false,
                    startBinary = true,
                    id = id,
                    messageId = messageId,
                    endpoint = endpoint,
                    data = binaryChannel
                )
            )
            launch {
                val channel = response.readBinary()
                var id = 0
                while (!channel.isClosedForRead) {
                    val packet = channel.readRemaining(maxSize).readBytes()
                    if (packet.isNotEmpty()) {
                        send(
                            Packet(
                                input = input,
                                binary = true,
                                startBinary = false,
                                id = binaryChannel,
                                messageId = id++.toString(),
                                endpoint = endpoint,
                                data = packet.encodeBase64()
                            )
                        )
                    }
                }
                send(
                    Packet(
                        input = input,
                        binary = true,
                        startBinary = false,
                        id = binaryChannel,
                        messageId = id.toString(),
                        endpoint = endpoint,
                        data = ByteArray(0).encodeBase64()
                    )
                )
            }
        } else {
            send(
                Packet(
                    input = input,
                    binary = false,
                    startBinary = false,
                    id = id,
                    messageId = messageId,
                    endpoint = endpoint,
                    data = response.readSerialized()
                )
            )
        }
    }

    private suspend fun getCallData(packet: Packet): CallData<String> {
        return if (packet.startBinary) {
            CallData.createBinary(getByteChannel(packet.data))
        } else if (packet.binary) {
            error("Unexpected binary packet")
        } else {
            CallData.create(packet.data)
        }
    }

    private suspend fun removeBinaryChannelIfDone(channel: BinaryChannel) {
        if (!channel.isDone) {
            return
        }
        binaryChannelLock.lock()
        try {
            binaryChannels.remove(channel.id)
        } finally {
            binaryChannelLock.unlock()
        }
    }

    private suspend fun getBinaryChannel(id: String): BinaryChannel {
        binaryChannelLock.lock()
        try {
            return binaryChannels.getOrPut(id) {
                BinaryChannel(id)
            }
        } finally {
            binaryChannelLock.unlock()
        }
    }

    private suspend fun getByteChannel(data: String): ByteChannel {
        val binaryChannel = getBinaryChannel(data)
        return binaryChannel.getByteChannel().also {
            removeBinaryChannelIfDone(binaryChannel)
        }
    }

    override suspend fun wrapChannel(channelId: ChannelId): SerializedService<String> {
        return SubserviceChannel(this, channelId)
    }

    override suspend fun call(channelId: ChannelId, endpoint: String, data: CallData<String>): CallData<String> {
        val (messageId, response) = multiChannel.allocateReceive()
        scope.sendPacket(true, channelId.id, messageId.toString(), endpoint, data)
        return getCallData(response.await())
    }

    override suspend fun close(id: ChannelId) {
        val serviceChannel = serviceChannel
        serviceChannel.close(id)
        call(id, "", CallData.create("{}"))
    }

    override suspend fun registerDefault(service: SerializedService<String>) {
        val serviceChannel = serviceChannel
        serviceChannel.registerDefault(service)
    }

    override suspend fun registerHost(service: SerializedService<String>): ChannelId {
        val serviceChannel = serviceChannel
        return serviceChannel.registerHost(service)
    }

    override suspend fun close() {
        callLock.lock()
        try {
            if (isClosed) return
            multiChannel.close()
            binaryChannels.values.forEach {
                it.channel.close()
            }
            serviceChannel.close()
            isClosed = true
            onCloseObservers.forEach { it.invoke() }
        } finally {
            callLock.unlock()
        }
    }

    override suspend fun onClose(onClose: suspend () -> Unit) {
        onCloseObservers.add(onClose)
    }

    private class BinaryChannel(
        val id: String,
        val channel: ByteChannel = ByteChannel(),
        var currentPacket: Int = 0,
        var pending: MutableMap<Int, Packet> = mutableMapOf()
    ) {
        private var hasClosedChannel: Boolean = false
        private var hasGottenChannel: Boolean = false
        val isDone: Boolean
            get() = hasClosedChannel && hasGottenChannel

        suspend fun handlePacket(packet: Packet): Boolean {
            if (packet.messageId.toInt() == currentPacket) {
                currentPacket++
                if (packet.data.isEmpty()) {
                    channel.flush()
                    channel.close()
                    hasClosedChannel = true
                    return true
                } else {
                    val data = packet.data.decodeBase64Bytes()
                    channel.writeFully(data, 0, data.size)
                    pending[currentPacket]?.let { handlePacket(it) }
                }
            } else {
                pending[packet.messageId.toInt()] = packet
            }
            return false
        }

        fun getByteChannel(): ByteChannel {
            return channel.also {
                hasGottenChannel = true
            }
        }
    }

    private data class PendingPacket(
        val receivedAt: Long,
        val packet: Packet
    )
}
