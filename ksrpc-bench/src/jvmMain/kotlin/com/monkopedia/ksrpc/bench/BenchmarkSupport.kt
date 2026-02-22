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
package com.monkopedia.ksrpc.bench

import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.channels.CallData
import com.monkopedia.ksrpc.channels.SerializedService
import java.net.ServerSocket
import kotlinx.serialization.builtins.serializer

internal class EchoSerializedService(override val env: KsrpcEnvironment<String>) :
    SerializedService<String> {
    private val onCloseHandlers = mutableSetOf<suspend () -> Unit>()

    override suspend fun call(endpoint: String, input: CallData<String>): CallData<String> = input

    override suspend fun close() {
        onCloseHandlers.forEach { it.invoke() }
    }

    override suspend fun onClose(onClose: suspend () -> Unit) {
        onCloseHandlers.add(onClose)
    }
}

internal suspend fun callEcho(
    service: SerializedService<String>,
    env: KsrpcEnvironment<String>,
    payload: String
): String {
    val input = env.serialization.createCallData(String.serializer(), payload)
    val output = service.call("echo", input)
    return env.serialization.decodeCallData(String.serializer(), output)
}

internal fun reserveFreePort(): Int = ServerSocket(0).use { socket ->
    socket.reuseAddress = true
    socket.localPort
}
