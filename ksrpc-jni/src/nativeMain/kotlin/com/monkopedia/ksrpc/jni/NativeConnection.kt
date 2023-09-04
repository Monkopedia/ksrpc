@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.ksrpc.jni

import com.monkopedia.jni.JNIEnvVar
import com.monkopedia.jni.jlong
import com.monkopedia.jni.jobject
import com.monkopedia.jnitest.JNI
import com.monkopedia.jnitest.com.monkopedia.ksrpc.jni.JavaJniContinuation
import com.monkopedia.jnitest.com.monkopedia.ksrpc.jni.JavaJniContinuationConverter
import com.monkopedia.jnitest.initThread
import com.monkopedia.jnitest.threadEnv
import com.monkopedia.jnitest.threadJni
import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.channels.CallData
import com.monkopedia.ksrpc.packets.internal.Packet
import com.monkopedia.ksrpc.packets.internal.PacketChannelBase
import kotlin.coroutines.suspendCoroutine
import kotlinx.cinterop.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

class NativeConnection(
    scope: CoroutineScope,
    private val objectRef: jobject,
    env: KsrpcEnvironment<JniSerialized>
) : PacketChannelBase<JniSerialized>(scope, env) {
    private val receiveChannel = Channel<Packet<JniSerialized>>()
    private val sendLock = Mutex()
    private val receiveLock = Mutex()
    private val typeConverter = newTypeConverter<Any?>()

    override suspend fun send(packet: Packet<JniSerialized>) {
        sendLock.lock()
        try {
            val serialized = env.serialization.createCallData(
                Packet.serializer(JniSerialized),
                packet
            )
            suspendCoroutine<Int> {
                JNI.JniConnection.sendFromNative(
                    objectRef,
                    serialized.readSerialized().toJvm(threadEnv),
                    NativeJniContinuationConverter<Int>(threadEnv).convertFrom(
                        it.withConverter(typeConverter.int)
                    )
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

    override suspend fun close() {
        suspendCoroutine {
            JNI.JniConnection.closeFromNative(
                NativeJniContinuationConverter<Int>(threadEnv).convertFrom(
                    it.withConverter(typeConverter.int)
                )!!,
                objectRef
            )
        }
    }

    internal fun closeFromJvm(continuation: JavaJniContinuation<Int>) {
        scope.launch {
            try {
                super.close()
                receiveChannel.close()
                continuation.resumeWith(typeConverter.int, Result.success(0))
            } catch (t: Throwable) {
                continuation.resumeWith(typeConverter.int, Result.failure(t))
            }
        }
    }

    fun sendFromJvm(packet: JniSerialized, continuation: JavaJniContinuation<Int>) {
        scope.launch {
            val result = runCatching {
                val p = env.serialization.decodeCallData(
                    Packet.serializer(JniSerialized),
                    CallData.create(packet)
                )
                receiveChannel.send(p)
                0
            }
            continuation.resumeWith(typeConverter.int, result)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
@CName("Java_com_monkopedia_ksrpc_jni_JniConnection_finalize")
fun jniConnectionFinalize(
    env: CPointer<JNIEnvVar>,
    clazz: jobject,
    nativeObject: jlong,
    nativeEnvironment: jlong
) {
    initThread(env)
    try {
        nativeObject.toCPointer<CPointed>()?.asStableRef<NativeConnection>()?.dispose()
        nativeEnvironment.toCPointer<CPointed>()?.asStableRef<KsrpcEnvironment<jobject>>()?.dispose()
    } catch (t: Throwable) {
        t.printStackTrace()
    }
}

@OptIn(ExperimentalForeignApi::class)
@CName("Java_com_monkopedia_ksrpc_jni_JniConnection_createConnection")
fun jniCreateConnection(env: CPointer<JNIEnvVar>, clazz: jobject, scope: jlong, ksrpcEnv: jlong): jlong {
    initThread(env)
    try {
        val nativeScope = scope.toCPointer<CPointed>()?.asStableRef<CoroutineScope>()?.get()
            ?: return -1
        val nativeEnv = ksrpcEnv.toCPointer<CPointed>()?.asStableRef<KsrpcEnvironment<JniSerialized>>()?.get()
            ?: return -1
        val objectRef = threadJni.NewWeakGlobalRef!!.invoke(env, clazz)
        val connection = NativeConnection(nativeScope, objectRef!!, nativeEnv)
        return StableRef.create(connection).asCPointer().rawValue.toLong()
    } catch (t: Throwable) {
        t.printStackTrace()
        return -1
    }
}

@OptIn(ExperimentalForeignApi::class)
@CName("Java_com_monkopedia_ksrpc_jni_JniConnection_close")
fun jniConnectionClose(
    env: CPointer<JNIEnvVar>,
    clazz: jobject,
    nativeObject: jlong,
    continuation: jobject
) {
    initThread(env)
    try {
        val connection = nativeObject.toCPointer<CPointed>()?.asStableRef<NativeConnection>()?.get()
            ?: return
        val continuation = JavaJniContinuationConverter<Int>(env).convertTo(continuation)
        connection.closeFromJvm(continuation)
    } catch (t: Throwable) {
        t.printStackTrace()
    }
}

@OptIn(ExperimentalForeignApi::class)
@CName("Java_com_monkopedia_ksrpc_jni_JniConnection_sendSerialized")
fun jniConnectionSendSerialized(
    env: CPointer<JNIEnvVar>,
    clazz: jobject,
    nativeObject: jlong,
    packet: jobject,
    continuation: jobject
) {
    initThread(env)
    try {
        val connection = nativeObject.toCPointer<CPointed>()?.asStableRef<NativeConnection>()?.get()
            ?: return
        val continuation = JavaJniContinuationConverter<Int>(env).convertTo(continuation)
        val packet = JniSerialized.fromJvm(env, packet)
        connection.sendFromJvm(packet, continuation)
    } catch (t: Throwable) {
        t.printStackTrace()
    }
}
