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

package com.monkopedia.ksrpc

import com.monkopedia.ksrpc.annotation.KsrpcInternal
import com.monkopedia.ksrpc.channels.CallData
import com.monkopedia.ksrpc.packets.internal.PACKET_JSON
import com.monkopedia.ksrpc.packets.internal.Packet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Regression tests for #70.
 *
 * The packet envelope is serialized with [PACKET_JSON], which has `ignoreUnknownKeys = true`
 * so that `Packet` can gain new fields in future releases without breaking peers compiled
 * against the older schema. These tests pin that behavior and confirm the user's own
 * [CallDataSerializer] (for user-declared method inputs/outputs) is *not* affected — a user
 * who chooses a strict Json still gets strict decoding for their own data.
 */
class PacketSchemaEvolutionTest {

    @Test
    fun packetJsonDecodesPacketWithUnknownField() {
        // Simulate a newer peer that added an "x" key to the Packet wire schema.
        val newerWirePayload = """
            {
                "t": 1,
                "i": "chan-1",
                "m": "msg-1",
                "e": "/foo",
                "d": "payload",
                "x": "future-field-value"
            }
        """.trimIndent()

        // Old peer decoding newer wire packet: unknown "x" must be ignored silently.
        val decoded = PACKET_JSON.decodeFromString(
            Packet.serializer(String.serializer()),
            newerWirePayload
        )

        assertEquals(1, decoded.type)
        assertEquals("chan-1", decoded.id)
        assertEquals("msg-1", decoded.messageId)
        assertEquals("/foo", decoded.endpoint)
        assertEquals("payload", decoded.data)
    }

    @Test
    fun packetJsonRoundTripsStandardPacket() {
        val packet = Packet(
            input = true,
            binary = false,
            id = "chan",
            messageId = "msg",
            endpoint = "/path",
            data = "body"
        )

        val wire = PACKET_JSON.encodeToString(Packet.serializer(String.serializer()), packet)
        val roundTripped = PACKET_JSON.decodeFromString(
            Packet.serializer(String.serializer()),
            wire
        )

        assertEquals(packet, roundTripped)
    }

    @Test
    fun userStrictSerializationUnaffectedByPacketJson() {
        // A user-configured strict Json remains strict for user-declared payload types.
        // The packet envelope is handled separately by PACKET_JSON at the transport layer,
        // so the user's choice of strictness for their own data is preserved.
        val strictEnv = ksrpcEnvironment(Json { ignoreUnknownKeys = false }) { }

        val userPayloadWithUnknown = """{"known":"value","unknown":"x"}"""

        assertFailsWith<SerializationException> {
            strictEnv.serialization.decodeCallData(
                KnownOnly.serializer(),
                CallData.create(userPayloadWithUnknown)
            )
        }
    }

    @kotlinx.serialization.Serializable
    private data class KnownOnly(val known: String)
}
