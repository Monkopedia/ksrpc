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
import com.monkopedia.ksrpc.packets.internal.PacketTlv
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Covers the TLV metadata contract added in issue #70.
 *
 * The critical guarantee under test: a packet produced by a peer that adds a metadata tag
 * the current decoder has never heard of must still decode cleanly, with the unknown tag
 * preserved (or at minimum ignored) — that is the forward-compat contract.
 */
class PacketMetadataTlvTest {

    private val json = Json
    private val serializer = Packet.serializer(String.serializer())

    @Test
    fun emptyMetadataIsOmittedFromWire() {
        // With @EncodeDefault(NEVER) the field must not appear in the serialised form when
        // empty. This keeps packets without metadata byte-identical to the pre-TLV format
        // for already-connected empty flows.
        val packet = Packet(
            input = true,
            id = "channel",
            messageId = "42",
            endpoint = "/do",
            data = "payload"
        )

        val encoded = json.encodeToString(serializer, packet)

        assertFalse("\"x\"" in encoded, "expected metadata key 'x' absent when empty: $encoded")
    }

    @Test
    fun roundTripPreservesKnownMetadataEntries() {
        val packet = Packet(
            input = false,
            id = "channel",
            messageId = "7",
            endpoint = "/do",
            data = "payload",
            metadata = mapOf(
                PacketTlv.CONTEXT_MAP to byteArrayOf(1, 2, 3, 4),
                42 to byteArrayOf(9, 8)
            )
        )

        val encoded = json.encodeToString(serializer, packet)
        val decoded = json.decodeFromString(serializer, encoded)

        assertEquals(2, decoded.metadata.size)
        assertTrue(decoded.metadata[PacketTlv.CONTEXT_MAP]!!.contentEquals(byteArrayOf(1, 2, 3, 4)))
        assertTrue(decoded.metadata[42]!!.contentEquals(byteArrayOf(9, 8)))
        assertEquals(packet, decoded)
    }

    @Test
    fun forwardCompatUnknownTagSurvivesDecode() {
        // Simulate a "future" peer adding a tag (99) that the current code has no awareness
        // of. The decoder must still succeed; consumers looking for tag 99 would find the
        // raw bytes, while consumers only aware of (say) CONTEXT_MAP skip it entirely. This
        // is the contract that makes post-1.0 additions non-breaking.
        val future = Packet(
            input = true,
            id = "channel",
            messageId = "1",
            endpoint = "/do",
            data = "payload",
            metadata = mapOf(99 to "hello".encodeToByteArray())
        )

        val encoded = json.encodeToString(serializer, future)
        val decoded = json.decodeFromString(serializer, encoded)

        // Current code doesn't know tag 99 exists; it should still round-trip.
        assertEquals(1, decoded.metadata.size)
        assertTrue(decoded.metadata[99]!!.contentEquals("hello".encodeToByteArray()))

        // And a consumer that only cares about CONTEXT_MAP sees nothing for it, no crash.
        assertEquals(null, decoded.metadata[PacketTlv.CONTEXT_MAP])
    }

    @Test
    fun decodingPreTlvPayloadYieldsEmptyMetadata() {
        // A packet produced by an older encoder (pre-#70) has no 'x' field. The default
        // value on the constructor means decoding gives an empty map, no failure.
        val legacyWire = """{"t":1,"i":"channel","m":"1","e":"/do","d":"payload"}"""

        val decoded = json.decodeFromString(serializer, legacyWire)

        assertTrue(decoded.metadata.isEmpty())
        assertEquals("channel", decoded.id)
        assertEquals("1", decoded.messageId)
    }
}
