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
package com.monkopedia.ksrpc.packets.internal

import com.monkopedia.ksrpc.SuspendCloseable
import com.monkopedia.ksrpc.annotation.KsrpcInternal
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire-format packet exchanged by [PacketChannelBase].
 *
 * The packet carries the existing core fields ([type], [id], [messageId], [endpoint], [data])
 * plus an extensible [metadata] TLV-style section. [metadata] is a map from 16-bit tag to raw
 * opaque bytes; consumers decode each tag according to their own rules and silently ignore
 * tags they don't recognise. The map defaults to empty and is omitted from the wire when empty,
 * so packets without metadata are byte-identical to the pre-TLV format.
 *
 * Reserved tag values are documented in [PacketTlv]; tag numbers are allocated there, but the
 * library performs no validation beyond "decoding unknown tags must not fail."
 *
 * See issue #70. This is the one-time wire break; from this release forward, new metadata
 * tags can be added without requiring peer upgrades.
 */
@KsrpcInternal
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Packet<T>(
    @SerialName("t")
    val type: Int = 0,
    @SerialName("i")
    val id: String,
    @SerialName("m")
    val messageId: String,
    @SerialName("e")
    val endpoint: String,
    @SerialName("d")
    val data: T,
    /**
     * Extensible metadata section: a map from 16-bit tag number to opaque bytes. Empty by
     * default; when empty the field is omitted from the wire (see [EncodeDefault.Mode.NEVER]).
     *
     * Consumers reading this map should look up the specific tags they care about and ignore
     * the rest — this is what makes future additions forward-compatible.
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("x")
    val metadata: Map<Int, ByteArray> = emptyMap()
) {
    val input: Boolean get() = (type and 1) != 0
    val binary: Boolean get() = (type and 2) != 0
    val startBinary: Boolean get() = (type and 4) != 0

    /**
     * Cancel frame flag (bit 3, value 8). When set, the packet carries no payload and instructs
     * the receiver to cancel the handler previously associated with [messageId] on channel [id].
     *
     * Cancel frames are directional: client-to-server when a caller coroutine is cancelled.
     */
    val cancel: Boolean get() = (type and 8) != 0

    constructor(
        input: Boolean = false,
        binary: Boolean = false,
        startBinary: Boolean = false,
        cancel: Boolean = false,
        id: String,
        messageId: String,
        endpoint: String,
        data: T,
        metadata: Map<Int, ByteArray> = emptyMap()
    ) : this(
        (if (input) 1 else 0) or
            (if (binary) 2 else 0) or
            (if (startBinary) 4 else 0) or
            (if (cancel) 8 else 0),
        id,
        messageId,
        endpoint,
        data,
        metadata
    )

    // equals/hashCode: ByteArray identity is reference-based by default, which is wrong for
    // a value-style data class. Override so test round-trips can compare cleanly.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Packet<*>) return false
        if (type != other.type) return false
        if (id != other.id) return false
        if (messageId != other.messageId) return false
        if (endpoint != other.endpoint) return false
        if (data != other.data) return false
        if (metadata.size != other.metadata.size) return false
        for ((k, v) in metadata) {
            val ov = other.metadata[k] ?: return false
            if (!v.contentEquals(ov)) return false
        }
        return true
    }

    override fun hashCode(): Int {
        var result = type
        result = 31 * result + id.hashCode()
        result = 31 * result + messageId.hashCode()
        result = 31 * result + endpoint.hashCode()
        result = 31 * result + (data?.hashCode() ?: 0)
        for ((k, v) in metadata) {
            result = 31 * result + k
            result = 31 * result + v.contentHashCode()
        }
        return result
    }
}

@KsrpcInternal
internal interface PacketChannel<T> : SuspendCloseable {
    suspend fun sendLocked(packet: Packet<T>)
    suspend fun receiveLocked(): Packet<T>
}
