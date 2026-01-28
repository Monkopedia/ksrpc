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
@file:OptIn(ExperimentalWasmJsInterop::class)
@file:Suppress("UnsafeCastFromDynamic")

package com.monkopedia.ksrpc.webworker

import com.monkopedia.ksrpc.CallDataSerializer
import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.channels.CallData
import com.monkopedia.ksrpc.channels.Connection
import com.monkopedia.ksrpc.packets.internal.Packet
import com.monkopedia.ksrpc.packets.internal.PacketChannelBase
import kotlin.js.Promise
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.await
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.serializer

actual fun createServiceWorkerWithConnection(
    workerScriptPath: String,
    env: KsrpcEnvironment<String>
): Connection<String> {
    val connection = ServiceWorkerPacketChannel(env.defaultScope, env)
    env.defaultScope.launch {
        val registration = registerServiceWorker(workerScriptPath).await<JsAny?>()
            ?: return@launch
        val worker = resolveServiceWorker(registration)
            ?: resolveServiceWorker(serviceWorkerReady().await<JsAny?>() ?: return@launch)
        if (worker == null) {
            env.logger.warn("ServiceWorker", "No active service worker for $workerScriptPath")
            return@launch
        }
        val channel = createMessageChannel()
        connection.attachPort(messageChannelPort1(channel))
        postServiceWorkerConnect(worker, messageChannelPort2(channel))
    }
    return connection
}

private fun ServiceWorkerPacketChannel.attachPort(port: JsAny) {
    if (!setPort(port)) return
    portOnMessage(port) { payload ->
        if (payload != null) {
            handleIncoming(payload)
        }
    }
}

private class ServiceWorkerPacketChannel(scope: CoroutineScope, env: KsrpcEnvironment<String>) :
    PacketChannelBase<String>(scope, env) {
    private val incoming = Channel<Packet<String>>(Channel.UNLIMITED)
    private val portDeferred = CompletableDeferred<JsAny>()

    fun setPort(port: JsAny): Boolean {
        if (portDeferred.isCompleted) return false
        portDeferred.complete(port)
        return true
    }

    fun handleIncoming(payload: String) {
        val packet = decodePacket(payload, env.serialization)
        incoming.trySend(packet)
    }

    override suspend fun sendLocked(packet: Packet<String>) {
        val message = encodePacket(packet, env.serialization)
        postMessage(portDeferred.await(), message)
    }

    override suspend fun receiveLocked(): Packet<String> = incoming.receive()

    override suspend fun close() {
        super.close()
        incoming.close()
        if (portDeferred.isCompleted) {
            closePort(portDeferred.await())
        }
    }
}

private fun encodePacket(
    packet: Packet<String>,
    serialization: CallDataSerializer<String>
): String = serialization
    .createCallData(Packet.serializer(String.serializer()), packet)
    .readSerialized()

private fun decodePacket(
    payload: String,
    serialization: CallDataSerializer<String>
): Packet<String> {
    val callData = CallData.create(payload)
    return serialization.decodeCallData(Packet.serializer(String.serializer()), callData)
}

private val registerServiceWorker: (String) -> Promise<JsAny?> =
    js("(path) => globalThis.navigator.serviceWorker.register(path)")

private val serviceWorkerReady: () -> Promise<JsAny?> =
    js("() => globalThis.navigator.serviceWorker.ready")

private val resolveServiceWorker: (JsAny) -> JsAny? =
    js(
        "(registration) => registration && " +
            "(registration.active || registration.waiting || registration.installing)"
    )

private val createMessageChannel: () -> JsAny =
    js("() => new MessageChannel()")

private val messageChannelPort1: (JsAny) -> JsAny =
    js("(channel) => channel.port1")

private val messageChannelPort2: (JsAny) -> JsAny =
    js("(channel) => channel.port2")

private val postServiceWorkerConnect: (JsAny, JsAny) -> Unit =
    js("(worker, port) => worker.postMessage('ksrpc-connect', [port])")

private val postMessage: (JsAny, String) -> Unit =
    js("(port, message) => port.postMessage(message)")

private val portOnMessage: (JsAny, (String?) -> Unit) -> Unit =
    js(
        "(port, callback) => { " +
            "port.onmessage = " +
            "(event) => callback(event.data == null ? null : event.data.toString()); " +
            "}"
    )

private val closePort: (JsAny) -> Unit =
    js("(port) => port.close()")
