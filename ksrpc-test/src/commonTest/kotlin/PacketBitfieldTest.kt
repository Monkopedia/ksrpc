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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PacketBitfieldTest {

    @Test
    fun booleanConstructorBuildsExpectedTypeBits() {
        val packet = Packet(
            input = true,
            binary = false,
            startBinary = true,
            id = "id",
            messageId = "message",
            endpoint = "/endpoint",
            data = "payload"
        )

        assertEquals(5, packet.type)
        assertTrue(packet.input)
        assertFalse(packet.binary)
        assertTrue(packet.startBinary)
    }

    @Test
    fun typeConstructorExposesBooleanFlags() {
        val packet = Packet(
            type = 6,
            id = "id",
            messageId = "message",
            endpoint = "/endpoint",
            data = "payload"
        )

        assertFalse(packet.input)
        assertTrue(packet.binary)
        assertTrue(packet.startBinary)
    }
}
