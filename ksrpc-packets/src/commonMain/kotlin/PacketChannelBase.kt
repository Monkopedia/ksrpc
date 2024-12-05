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
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.close
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.writeFully
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.builtins.serializer

private const val DEFAULT_MAX_SIZE = 16 * 1024L

abstract class PacketChannelBase<T>(
    protected val scope: CoroutineScope,
    final override val env: KsrpcEnvironment<T>
) : PacketChannel<T>, Connection<T>, ChannelHost<T> {
    private val sendLock = Mutex()
    private val receiveLock = Mutex()
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
    private val binaryChannels = mutableMapOf<String, BinaryChannel<T>>()

    private val multiChannel = MultiChannel<Packet<T>>()

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
            env.logger.warn("MultiChannel", "Exception in multichannel", t)
            binaryChannels.values.forEach { it.channel.close(t) }
            multiChannel.close(CancellationException("Multi-channel failure", t))
        }
    }

    private suspend fun CoroutineScope.sendPacket(
        input: Boolean,
        id: String,
        messageId: String,
        endpoint: String,
        response: CallData<T>
    ) {
        if (response.isBinary) {
            val binaryChannel = randomUuid()
            env.logger.debug(
                "SerializedChannel",
                "Beginning binary packetization for $binaryChannel"
            )
            send(
                Packet(
                    input = input,
                    binary = false,
                    startBinary = true,
                    id = id,
                    messageId = messageId,
                    endpoint = endpoint,
                    data = env.serialization.createCallData(
                        String.serializer(),
                        binaryChannel
                    ).readSerialized()
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
                                data = env.serialization.createCallData(
                                    ByteArraySerializer(),
                                    packet
                                ).readSerialized()
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
                        data = env.serialization.createCallData(
                            ByteArraySerializer(),
                            ByteArray(0)
                        ).readSerialized()
                    )
                )
                env.logger.debug(
                    "SerializedChannel",
                    "Completed binary packetization for $binaryChannel"
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

    private suspend fun getCallData(packet: Packet<T>): CallData<T> {
        return if (packet.startBinary) {
            val callData = CallData.create(packet.data)
            val decoded = env.serialization.decodeCallData(String.serializer(), callData)
            CallData.createBinary(getByteChannel(decoded))
        } else if (packet.binary) {
            error("Unexpected binary packet")
        } else {
            CallData.create(packet.data)
        }
    }

    private suspend fun removeBinaryChannelIfDone(channel: BinaryChannel<T>) {
        if (!channel.isDone) {
            return
        }
        env.logger.debug("SerializedChannel", "Removing complete channel ${channel.id}")
        binaryChannelLock.lock()
        try {
            binaryChannels.remove(channel.id)
        } finally {
            binaryChannelLock.unlock()
        }
    }

    private suspend fun getBinaryChannel(id: String): BinaryChannel<T> {
        binaryChannelLock.lock()
        try {
            return binaryChannels.getOrPut(id) {
                env.logger.info("SerializedChannel", "Creating binary channel $id")
                BinaryChannel(id, env)
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

    override suspend fun wrapChannel(channelId: ChannelId): SerializedService<T> {
        env.logger.debug("SerializedChannel", "Wrapping channel ${channelId.id}")
        return SubserviceChannel(this, channelId)
    }

    override suspend fun call(
        channelId: ChannelId,
        endpoint: String,
        data: CallData<T>
    ): CallData<T> {
        val (messageId, response) = multiChannel.allocateReceive()
        env.logger.debug(
            "SerializedChannel",
            "Sending call ${channelId.id}/$endpoint -  $messageId"
        )
        scope.sendPacket(true, channelId.id, messageId.toString(), endpoint, data)
        return getCallData(response.await())
    }

    override suspend fun close(id: ChannelId) {
        val serviceChannel = serviceChannel
        serviceChannel.close(id)
        env.logger.info("SerializedChannel", "Closing channel ${id.id}")
        call(id, "", env.serialization.createCallData(Unit.serializer(), Unit))
    }

    override suspend fun registerDefault(service: SerializedService<T>) {
        val serviceChannel = serviceChannel
        serviceChannel.registerDefault(service)
    }

    override suspend fun registerHost(service: SerializedService<T>): ChannelId {
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

    private class BinaryChannel<T>(
        val id: String,
        val env: KsrpcEnvironment<T>,
        val channel: ByteChannel = ByteChannel(),
        var currentPacket: Int = 0,
        var pending: MutableMap<Int, Packet<T>> = mutableMapOf()
    ) {
        private var hasClosedChannel: Boolean = false
        private var hasGottenChannel: Boolean = false
        val isDone: Boolean
            get() = hasClosedChannel && hasGottenChannel

        suspend fun handlePacket(packet: Packet<T>): Boolean {
            if (packet.messageId.toInt() == currentPacket) {
                currentPacket++
                val data = env.serialization.decodeCallData(
                    ByteArraySerializer(),
                    CallData.create(packet.data)
                )
                if (data.isEmpty()) {
                    channel.flush()
                    channel.close()
                    hasClosedChannel = true
                    return true
                } else {
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
    suspend fun send(packet: Packet<T>) {
        return sendLock.withLock {
            sendLocked(packet)
        }
    }

    suspend fun receive(): Packet<T> {
        return receiveLock.withLock {
            receiveLocked()
        }
    }

    private data class PendingPacket<T>(
        val receivedAt: Long,
        val packet: Packet<T>
    )
}
