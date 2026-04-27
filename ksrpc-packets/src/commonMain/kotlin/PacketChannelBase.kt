/*
 * Copyright (C) 2026 Jason Monk <monkopedia@gmail.com>
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
@file:OptIn(KsrpcInternal::class)

package com.monkopedia.ksrpc.packets.internal

import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.annotation.KsrpcInternal
import com.monkopedia.ksrpc.channels.CallData
import com.monkopedia.ksrpc.channels.CancellationSupport
import com.monkopedia.ksrpc.channels.ChannelHost
import com.monkopedia.ksrpc.channels.ChannelId
import com.monkopedia.ksrpc.channels.Connection
import com.monkopedia.ksrpc.channels.RpcBinaryData
import com.monkopedia.ksrpc.channels.RpcCallId
import com.monkopedia.ksrpc.channels.SerializedService
import com.monkopedia.ksrpc.channels.WireContextMap
import com.monkopedia.ksrpc.channels.awaitRequestCancellable
import com.monkopedia.ksrpc.internal.ClientChannelContext
import com.monkopedia.ksrpc.internal.HostChannelContext
import com.monkopedia.ksrpc.internal.HostSerializedChannelImpl
import com.monkopedia.ksrpc.internal.MultiChannel
import com.monkopedia.ksrpc.internal.SubserviceChannel
import com.monkopedia.ksrpc.internal.randomUuid
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.builtins.serializer

private const val DEFAULT_MAX_SIZE = 16 * 1024L
private const val RECEIVE_LOOP_START_GRACE_MS = 500L

@KsrpcInternal
abstract class PacketChannelBase<T>(
    protected val scope: CoroutineScope,
    final override val env: KsrpcEnvironment<T>
) : PacketChannel<T>,
    Connection<T>,
    ChannelHost<T>,
    CancellationSupport {
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
    private val receiveLoopStart = CompletableDeferred<Unit>()

    // Incoming handler jobs, keyed by the remote-supplied (channelId, messageId). Populated
    // when the server receives a call and launches the handler; removed on completion (via
    // [unregisterHandler]) or on incoming cancel (via [cancelHandler]).
    //
    // Access is single-threaded in practice: the receive loop serialises all mutations from
    // that side, and completion-side removes run in the handler's own coroutine which races
    // only with the (equally rare) incoming cancel — both touch the same concurrent map and
    // neither cares which one wins.
    private val handlers = mutableMapOf<PacketCallId, Job>()

    init {
        scope.launch {
            withContext(context) {
                val hasStarted = withTimeoutOrNull(RECEIVE_LOOP_START_GRACE_MS) {
                    receiveLoopStart.await()
                } != null
                if (!hasStarted) {
                    env.logger.warn(
                        "PacketChannel",
                        "startReceiveLoop() was not called within " +
                            "${RECEIVE_LOOP_START_GRACE_MS}ms. " +
                            "Falling back to legacy startup behavior; " +
                            "explicit startup will be required in a future release."
                    )
                }
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
                if (p.cancel) {
                    // Cancel frames carry no payload and resolve to the running handler job
                    // registered under (id, messageId). Client side also receives cancel
                    // frames in the future direction — currently unused but symmetric.
                    cancelHandler(PacketCallId(p.id, p.messageId))
                    continue
                }
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
                        val callId = PacketCallId(p.id, p.messageId)
                        val handlerJob = this.coroutineContext[Job]
                            ?: error("Handler launched without a Job in its context")
                        registerHandler(callId, handlerJob)
                        try {
                            val callData = getCallData(p)
                            // Strip the channel's Job so the handler coroutine's Job stays
                            // the parent — an incoming cancel frame cancels the handler and
                            // the cancellation must flow through into serviceChannel.call.
                            // The CurrentRpcCallElement is installed centrally in
                            // RpcMethod.call via the callId we pass through here.
                            // Install WireContextMap from the packet so RpcMethod.call can
                            // decode @KsContext bindings into real coroutine-context elements.
                            val wireCtx = p.contextMap?.let { WireContextMap(it) }
                            val handlerContext = if (wireCtx != null) {
                                context.minusKey(Job) + wireCtx
                            } else {
                                context.minusKey(Job)
                            }
                            val response = withContext(handlerContext) {
                                serviceChannel.call(
                                    ChannelId(p.id),
                                    p.endpoint,
                                    callData,
                                    callId
                                )
                            }

                            sendPacket(false, p.id, p.messageId, p.endpoint, response)
                        } finally {
                            unregisterHandler(callId)
                        }
                    } else {
                        multiChannel.send(p.messageId, p)
                    }
                }
            }
        } catch (t: Throwable) {
            env.logger.warn("MultiChannel", "Exception in multichannel", t)
            binaryChannels.values.forEach { it.closeWithError(t) }
            multiChannel.close(CancellationException("Multi-channel failure", t))
        }
    }

    /**
     * Starts the receive loop for this packet channel.
     *
     * Subclasses should call this from their own initialization once their state is ready.
     */
    protected fun startReceiveLoop() {
        receiveLoopStart.complete(Unit)
    }

    private suspend fun CoroutineScope.sendPacket(
        input: Boolean,
        id: String,
        messageId: String,
        endpoint: String,
        response: CallData<T>,
        contextMap: Map<String, String>? = null
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
                    ).readSerialized(),
                    contextMap = contextMap
                )
            )
            launch {
                val binaryData = response.readBinary()
                var id = 0
                val packetMax = maxSize.toInt().coerceAtLeast(1)
                binaryData.transferTo { bytes, offset, length ->
                    if (length <= 0) return@transferTo
                    // Re-chunk the incoming sink buffer into packets bounded by
                    // [maxSize]. The upstream transport guarantees nothing about
                    // per-call sizes, so split here.
                    var remaining = length
                    var cursor = offset
                    while (remaining > 0) {
                        val take = if (remaining > packetMax) packetMax else remaining
                        val packet = bytes.copyOfRange(cursor, cursor + take)
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
                        cursor += take
                        remaining -= take
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
        } else if (response is CallData.Error<T>) {
            // Error frame: errorCode + errorMessage travel in dedicated Packet fields,
            // and the optional typed payload (already wire-encoded as T) rides in the
            // existing data slot. When no payload is attached we send a Unit-encoded
            // placeholder so the wire schema's non-null `data` field stays satisfied;
            // the receiver discriminates on errorCode != null and constructs
            // CallData.Error(..., errorData = null).
            val payload = response.errorData
                ?: env.serialization
                    .createCallData(Unit.serializer(), Unit)
                    .readSerialized()
            send(
                Packet(
                    input = input,
                    binary = false,
                    startBinary = false,
                    id = id,
                    messageId = messageId,
                    endpoint = endpoint,
                    data = payload,
                    errorCode = response.errorCode,
                    errorMessage = response.errorMessage
                )
            )
        } else {
            send(
                Packet(
                    input = input,
                    binary = false,
                    startBinary = false,
                    id = id,
                    messageId = messageId,
                    endpoint = endpoint,
                    data = response.readSerialized(),
                    contextMap = contextMap
                )
            )
        }
    }

    private suspend fun getCallData(packet: Packet<T>): CallData<T> = if (packet.isError) {
        // The payload-less encode path uses a Unit-encoded placeholder in the data slot —
        // we keep it as the errorData T, and the routing-layer decoder (RpcMethod.decodeError)
        // ignores it because no @KsError binding will resolve to Unit. Distinguishing the
        // placeholder from a real payload here would require a separate flag bit; the
        // placeholder is harmless and the cost of always passing it through is one
        // synthetic decode attempt that returns null.
        CallData.Error(
            errorCode = packet.errorCode!!,
            errorMessage = packet.errorMessage ?: "",
            errorData = packet.data
        )
    } else if (packet.startBinary) {
        val callData = CallData.create(packet.data)
        val decoded = env.serialization.decodeCallData(String.serializer(), callData)
        CallData.createBinary(getBinaryData(decoded))
    } else if (packet.binary) {
        error("Unexpected binary packet")
    } else {
        CallData.create(packet.data)
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

    private suspend fun getBinaryData(data: String): RpcBinaryData {
        val binaryChannel = getBinaryChannel(data)
        return binaryChannel.getBinaryData().also {
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
        data: CallData<T>,
        callId: RpcCallId?
    ): CallData<T> {
        val (messageId, response) = multiChannel.allocateReceiveString()
        val wireCallId = PacketCallId(channelId.id, messageId)
        env.logger.debug(
            "SerializedChannel",
            "Sending call ${channelId.id}/$endpoint -  $messageId"
        )
        val wireCtx = coroutineContext[WireContextMap]?.values
        scope.sendPacket(true, channelId.id, messageId, endpoint, data, contextMap = wireCtx)
        val packet = try {
            awaitRequestCancellable(wireCallId, response)
        } catch (t: CancellationException) {
            // Cleanup the pending entry while the parent is being cancelled — must use
            // NonCancellable so Mutex.withLock doesn't immediately rethrow.
            withContext(NonCancellable) {
                multiChannel.cancelPending(messageId, t)
            }
            throw t
        }
        return getCallData(packet)
    }

    override suspend fun close(id: ChannelId) {
        val serviceChannel = serviceChannel
        serviceChannel.close(id)
        env.logger.info("SerializedChannel", "Closing channel ${id.id}")
        call(id, "", env.serialization.createCallData(Unit.serializer(), Unit), callId = null)
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
            handlers.values.forEach {
                it.cancel(CancellationException("PacketChannel closed"))
            }
            handlers.clear()
            binaryChannels.values.forEach {
                it.closeWithError(null)
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

    // region CancellationSupport

    override suspend fun sendCancel(callId: RpcCallId) {
        if (callId !is PacketCallId) return
        if (isClosed) return
        // Cancel frames carry no payload; we only encode a placeholder Unit because the wire
        // schema requires a non-null `data` of type T. Receivers must not inspect the data
        // field on cancel frames (handled by the early-return in the receive loop).
        val emptyData = env.serialization.createCallData(Unit.serializer(), Unit).readSerialized()
        try {
            send(
                Packet(
                    input = true,
                    cancel = true,
                    id = callId.channelId,
                    messageId = callId.messageId,
                    endpoint = "",
                    data = emptyData
                )
            )
        } catch (t: Throwable) {
            env.logger.debug("PacketChannel", "Ignoring failure sending cancel frame", t)
        }
    }

    override fun registerHandler(callId: RpcCallId, job: Job) {
        if (callId !is PacketCallId) return
        // Lock-free insert is fine here — each messageId is unique per direction, and the
        // receive loop is single-threaded.
        handlers[callId] = job
    }

    override fun unregisterHandler(callId: RpcCallId) {
        if (callId !is PacketCallId) return
        handlers.remove(callId)
    }

    override fun cancelHandler(callId: RpcCallId, cause: CancellationException?) {
        if (callId !is PacketCallId) return
        handlers.remove(callId)?.cancel(cause ?: CancellationException("Remote cancellation"))
    }

    // endregion

    /**
     * Bridges inbound binary packets onto the transport-agnostic [RpcBinaryData]
     * surface consumed by `ksrpc-core`. Uses a [Channel] of `ByteArray` chunks
     * rather than a ktor `ByteChannel` so `ksrpc-packets` does not need the
     * ktor-io dependency — the ktor-io adapter now lives in the dedicated
     * `ksrpc-binary-ktor` module.
     */
    private class BinaryChannel<T>(
        val id: String,
        val env: KsrpcEnvironment<T>,
        private val chunks: Channel<ByteArray> = Channel(Channel.UNLIMITED),
        var currentPacket: Int = 0,
        var pending: MutableMap<Int, Packet<T>> = mutableMapOf()
    ) {
        private var hasClosedChannel: Boolean = false
        private var hasGottenChannel: Boolean = false
        val isDone: Boolean
            get() = hasClosedChannel && hasGottenChannel

        suspend fun handlePacket(packet: Packet<T>): Boolean {
            val packetId = packet.messageId.toInt()
            if (packetId != currentPacket) {
                pending[packetId] = packet
                return false
            }

            var nextPacket: Packet<T>? = packet
            while (nextPacket != null) {
                currentPacket++
                val data = env.serialization.decodeCallData(
                    ByteArraySerializer(),
                    CallData.create(nextPacket.data)
                )
                if (data.isEmpty()) {
                    chunks.close()
                    hasClosedChannel = true
                    return true
                }
                chunks.send(data)
                nextPacket = if (pending.isEmpty()) null else pending.remove(currentPacket)
            }
            return false
        }

        fun getBinaryData(): RpcBinaryData {
            hasGottenChannel = true
            return ChannelBinaryData(chunks)
        }

        fun closeWithError(cause: Throwable?) {
            chunks.close(cause)
        }
    }

    /**
     * Adapter that drains a [Channel] of `ByteArray` chunks into the
     * [RpcBinaryData.transferTo] sink.
     */
    private class ChannelBinaryData(private val chunks: Channel<ByteArray>) : RpcBinaryData {
        override suspend fun transferTo(
            sink: suspend (bytes: ByteArray, offset: Int, length: Int) -> Unit
        ) {
            try {
                while (true) {
                    val chunk = chunks.receive()
                    if (chunk.isNotEmpty()) sink(chunk, 0, chunk.size)
                }
            } catch (_: ClosedReceiveChannelException) {
                // Normal end-of-stream.
            }
        }

        override suspend fun close() {
            chunks.cancel()
        }
    }
    suspend fun send(packet: Packet<T>) = sendLock.withLock {
        sendLocked(packet)
    }

    suspend fun receive(): Packet<T> = receiveLock.withLock {
        receiveLocked()
    }

    private data class PendingPacket<T>(val receivedAt: Long, val packet: Packet<T>)
}
