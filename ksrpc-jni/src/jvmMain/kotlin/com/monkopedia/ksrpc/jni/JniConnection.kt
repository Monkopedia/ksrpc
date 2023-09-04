package com.monkopedia.ksrpc.jni

import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.channels.CallData
import com.monkopedia.ksrpc.jni.com.monkopedia.ksrpc.jni.asNativeScope
import com.monkopedia.ksrpc.packets.internal.Packet
import com.monkopedia.ksrpc.packets.internal.PacketChannelBase
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

class JniConnection(
    scope: CoroutineScope,
    env: KsrpcEnvironment<JniSerialized>,
    private val nativeEnvironment: Long
) : PacketChannelBase<JniSerialized>(scope, env) {
    private val receiveChannel = Channel<Packet<JniSerialized>>()
    private val sendLock = Mutex()
    private val receiveLock = Mutex()
    private val nativeConnection = createConnection(scope.asNativeScope, nativeEnvironment)

    constructor(
        scope: CoroutineScope,
        env: KsrpcEnvironment<JniSerialized>,
        nativeEnvironmentFactory: NativeKsrpcEnvironmentFactory
    ) : this(scope, env, nativeEnvironmentFactory.createNativeEnvironment())

    fun finalize() {
        finalize(nativeConnection, nativeEnvironment)
    }

    override suspend fun send(packet: Packet<JniSerialized>) {
        sendLock.lock()
        try {
            val serialized = env.serialization.createCallData(
                Packet.serializer(JniSerialized),
                packet
            )
            suspendCoroutine<Int> {
                sendSerialized(
                    nativeConnection,
                    serialized.readSerialized(),
                    it.withConverter(newTypeConverter<Any?>().int)
                )
            }
        } finally {
            sendLock.unlock()
        }
    }

    override suspend fun receive(): Packet<JniSerialized> {
        receiveLock.lock()
        try {
            return receiveChannel.receive()
        } finally {
            receiveLock.unlock()
        }
    }

    fun sendFromNative(packet: JniSerialized, continuation: NativeJniContinuation<Int>) {
        scope.launch {
            val r = runCatching {
                val p = env.serialization.decodeCallData(
                    Packet.serializer(JniSerialized),
                    CallData.create(packet)
                )
                receiveChannel.send(p)
                0
            }
            continuation.asContinuation(newTypeConverter<Any?>().int).resumeWith(r)
        }
    }

    override suspend fun close() {
        super.close()
        receiveChannel.close()
        suspendCoroutine<Int> {
            close(nativeConnection, it.withConverter(newTypeConverter<Any?>().int))
        }
    }

    fun closeFromNative(continuation: NativeJniContinuation<Int>) {
        GlobalScope.launch {
            val result = runCatching {
                close()
                0
            }
            continuation.resumeWith(newTypeConverter<Any?>().int, result)
        }
    }

    external fun finalize(nativeObject: Long, nativeEnvironment: Long)

    external fun createConnection(scope: Long, env: Long): Long

    external fun close(nativeObject: Long, continuation: JavaJniContinuation<Int>)

    external fun sendSerialized(
        nativeObject: Long,
        packet: JniSerialized,
        continuation: JavaJniContinuation<Int>
    )
}
