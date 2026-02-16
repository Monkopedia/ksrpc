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
package com.monkopedia.ksrpc

import com.monkopedia.ksrpc.packets.internal.Packet
import com.monkopedia.ksrpc.packets.internal.PacketChannelBase
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope

private class MinimalPacketChannelBase(
    scope: CoroutineScope,
    env: KsrpcEnvironment<String>
) : PacketChannelBase<String>(scope, env) {
    init {
        startReceiveLoop()
    }

    override suspend fun sendLocked(packet: Packet<String>) = Unit

    override suspend fun receiveLocked(): Packet<String> =
        throw CancellationException("test receive loop stop")
}

class PacketChannelBaseMinimalTest {
    @Test
    fun packetChannelBaseCanBeExtendedAndClosed() = runBlockingUnit {
        val env = ksrpcEnvironment { }
        val channel = MinimalPacketChannelBase(this, env)
        var didClose = false
        channel.onClose { didClose = true }

        channel.close()

        assertTrue(didClose)
    }
}
