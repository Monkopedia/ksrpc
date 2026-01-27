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
@file:Suppress("UnsafeCastFromDynamic")

package com.monkopedia.ksrpc.webworker

import com.monkopedia.ksrpc.CallDataSerializer
import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.RpcService
import com.monkopedia.ksrpc.channels.CallData
import com.monkopedia.ksrpc.channels.Connection
import com.monkopedia.ksrpc.channels.SerializedService
import com.monkopedia.ksrpc.packets.internal.Packet
import com.monkopedia.ksrpc.packets.internal.PacketChannelBase
import com.monkopedia.ksrpc.serialized
import kotlinx.browser.window
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.await
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.serializer
import org.w3c.dom.MessageChannel
import org.w3c.dom.MessageEvent
import org.w3c.dom.MessagePort

actual fun createServiceWorkerWithConnection(
    workerScriptPath: String,
    env: KsrpcEnvironment<String>
): Connection<String> {
    val connection = ServiceWorkerPacketChannel(env.defaultScope, env)
    env.defaultScope.launch {
        val registration = window.navigator.serviceWorker
            .register(workerScriptPath)
            .await()
        val worker = resolveServiceWorker(registration)
        if (worker == null) {
            env.logger.warn("ServiceWorker", "No active service worker for $workerScriptPath")
            return@launch
        }
        val channel = MessageChannel()
        connection.attachPort(channel.port1)
        worker.postMessage("ksrpc-connect", arrayOf(channel.port2))
    }
    return connection
}

/**
 * Listens for "ksrpc-connect" to attach a port and provides callback to initialize a [Connection].
 */
fun onServiceWorkerConnection(
    env: KsrpcEnvironment<String>,
    onConnection: suspend (Connection<String>) -> Unit
) {
    val global = js("self")
    val handler: (MessageEvent) -> Unit = handler@{ event ->
        val message = event.data as? String
        if (message != "ksrpc-connect") return@handler
        val connection = ServiceWorkerPacketChannel(env.defaultScope, env)
        val port = event.ports.getOrNull(0)
        if (port != null) {
            connection.attachPort(port)
        }
        env.defaultScope.launch {
            onConnection(connection)
        }
    }
    global.addEventListener("message", handler)
}

private fun ServiceWorkerPacketChannel.attachPort(port: MessagePort) {
    if (!setPort(port)) return
    port.onmessage = { event ->
        val payload = event.data as? String
        if (payload != null) {
            handleIncoming(payload)
        }
    }
}

private class ServiceWorkerPacketChannel(scope: CoroutineScope, env: KsrpcEnvironment<String>) :
    PacketChannelBase<String>(scope, env) {
    private val incoming = Channel<Packet<String>>(Channel.UNLIMITED)
    private val portDeferred = CompletableDeferred<MessagePort>()

    fun setPort(port: MessagePort): Boolean {
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
        portDeferred.await().postMessage(message)
    }

    override suspend fun receiveLocked(): Packet<String> = incoming.receive()

    override suspend fun close() {
        super.close()
        incoming.close()
        if (portDeferred.isCompleted) {
            portDeferred.await().close()
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

private suspend fun resolveServiceWorker(registration: dynamic): dynamic {
    val active = registration.active ?: registration.waiting ?: registration.installing
    if (active != null) return active
    return window.navigator.serviceWorker.ready.await().active
}
