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

package com.monkopedia.ksrpc.jni

import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.annotation.KsrpcInternal
import com.monkopedia.ksrpc.channels.CallData
import com.monkopedia.ksrpc.packets.internal.Packet
import com.monkopedia.ksrpc.packets.internal.PacketChannelBase
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

class JniConnection(scope: CoroutineScope, env: KsrpcEnvironment<JniSerialized>) :
    PacketChannelBase<JniSerialized>(scope, env) {
    private val receiveChannel = Channel<Packet<JniSerialized>>()
    private var nativeConnection: Long = 0L

    /**
     * Attaches the opaque native connection handle produced by the native
     * `initialize` export and starts the receive loop. Called once, after
     * construction, because native initialization is asynchronous.
     */
    fun attachNative(handle: Long) {
        nativeConnection = handle
        startReceiveLoop()
    }

    fun getNativeConnection(): Long = nativeConnection

    fun finalize() {
        finalize(nativeConnection)
    }

    override suspend fun sendLocked(packet: Packet<JniSerialized>) {
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
    }

    override suspend fun receiveLocked(): Packet<JniSerialized> = receiveChannel.receive()

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

    external fun finalize(nativeObject: Long)

    external fun close(nativeObject: Long, continuation: JavaJniContinuation<Int>)

    external fun sendSerialized(
        nativeObject: Long,
        packet: JniSerialized,
        continuation: JavaJniContinuation<Int>
    )

    companion object : Converter<Any?, JniConnection> {
        override fun convertTo(rawValue: Any?): JniConnection {
            @Suppress("CAST_NEVER_SUCCEEDS")
            return (this as JniConnection)
        }

        override fun convertFrom(value: JniConnection): Any = value
    }
}
