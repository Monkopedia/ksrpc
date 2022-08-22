package com.monkopedia.ksrpc.internal

import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.channels.CallData
import com.monkopedia.ksrpc.channels.ChannelHost
import com.monkopedia.ksrpc.channels.ChannelId
import com.monkopedia.ksrpc.channels.Connection
import com.monkopedia.ksrpc.channels.SerializedService
import com.monkopedia.ksrpc.channels.randomUuid
import io.ktor.util.decodeBase64Bytes
import io.ktor.util.encodeBase64
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.close
import io.ktor.utils.io.core.readBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind.EXACTLY_ONCE
import kotlin.contracts.contract
import kotlin.coroutines.CoroutineContext

private const val DEFAULT_MAX_SIZE = 16 * 1024L

internal abstract class PacketChannelBase(
    private val scope: CoroutineScope,
    final override val env: KsrpcEnvironment
) : PacketChannel, Connection, ChannelHost {
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
    private val receiveChannels = arrayOfNulls<ReceiveChannel>(env.maxParallelReceives)
    private var receiveLock = Semaphore(env.maxParallelReceives)
    private val acquireChannelLock = Mutex()

    init {
        scope.launch {
            withContext(context) {
                executeReceive(this)
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
                        binaryChannelLock.withLock { channel.handlePacket(p) }
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
                        val channel = receiveChannels[p.messageId.toInt()]

                        if (channel != null) {
                            channel.channel.send(p)
                        } else {
                            env.errorListener.onError(
                                IllegalStateException(
                                    "Got packet $p for unexpected message id ${p.messageId}"
                                )
                            )
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            receiveChannels.filterNotNull().forEach { it.channel.close(t) }
        }
    }

    private suspend fun CoroutineScope.sendPacket(
        input: Boolean,
        id: String,
        messageId: String,
        endpoint: String,
        response: CallData
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

    private suspend fun getCallData(packet: Packet): CallData {
        return if (packet.startBinary) {
            CallData.create(getByteChannel(packet.data))
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
        binaryChannelLock.withLock {
            binaryChannels.remove(channel.id)
        }
    }

    private suspend fun getBinaryChannel(id: String): BinaryChannel {
        return binaryChannelLock.withLock {
            binaryChannels.getOrPut(id) {
                BinaryChannel(id)
            }
        }
    }

    private suspend fun getByteChannel(data: String): ByteChannel {
        val binaryChannel = getBinaryChannel(data)
        return binaryChannel.getByteChannel().also {
            removeBinaryChannelIfDone(binaryChannel)
        }
    }

    override suspend fun wrapChannel(channelId: ChannelId): SerializedService {
        return SubserviceChannel(this, channelId)
    }

    override suspend fun call(channelId: ChannelId, endpoint: String, data: CallData): CallData {
        return withChannel { channel ->
            val messageId = channel.id.toString()
            scope.sendPacket(true, channelId.id, messageId, endpoint, data)
            getCallData(channel.channel.receive())
        }
    }

    @OptIn(ExperimentalContracts::class)
    private suspend inline fun <T> withChannel(withChannel: suspend (ReceiveChannel) -> T): T {
        contract {
            callsInPlace(withChannel, EXACTLY_ONCE)
        }
        if (receiveChannels.size == 1) {
            return acquireChannelLock.withLock {
                val channel = channelFor(0)
                withChannel(channel)
            }
        }
        return receiveLock.withPermit {
            acquireChannelLock.lock()
            val channel = try {
                acquireChannel()
            } finally {
                acquireChannelLock.unlock()
            }
            try {
                withChannel(channel)
            } finally {
                acquireChannelLock.lock()
                try {
                    releaseChannel(channel)
                } finally {
                    acquireChannelLock.unlock()
                }
            }
        }
    }

    private fun channelFor(index: Int) =
        receiveChannels[index] ?: ReceiveChannel(index, Channel()).also {
            receiveChannels[index] = it
        }

    private fun acquireChannel(): ReceiveChannel {
        for (i in receiveChannels.indices) {
            if (receiveChannels[i]?.isLocked != true) {
                return channelFor(i).also {
                    it.isLocked = true
                }
            }
        }
        error("Holding semaphore $receiveLock but no channels available")
    }

    private fun releaseChannel(channel: ReceiveChannel) {
        channel.isLocked = false
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
            receiveChannels.filterNotNull().forEach {
                it.channel.close()
            }
            serviceChannel.close()
            isClosed = true
            onCloseObservers.forEach { it.invoke() }
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

    private data class ReceiveChannel(
        val id: Int,
        val channel: Channel<Packet>
    ) {
        var isLocked: Boolean = false
    }
}
