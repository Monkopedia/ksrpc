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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@KsrpcInternal
@Serializable
class Packet<T>(
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
     * Optional error code. When non-null, this frame carries a [com.monkopedia.ksrpc.channels.CallData.Error]
     * response: [data] holds the wire-format-encoded error payload (or a serializer-supplied
     * placeholder when no payload), and [errorMessage] holds the human-readable message. The
     * field's nullability is the discriminator — there is no separate flag bit. Older peers
     * that don't understand this field tolerate it via `ignoreUnknownKeys` on the packet JSON
     * (see [PACKET_JSON]) and will continue to decode the frame as a non-error response, so
     * back-compat for older receivers requires either a bumped wire version or a graceful
     * handler on the user side.
     */
    @SerialName("ec")
    val errorCode: Int? = null,
    /**
     * Optional human-readable error message accompanying [errorCode]. Always non-null when
     * [errorCode] is non-null; null otherwise.
     */
    @SerialName("em")
    val errorMessage: String? = null,
    /**
     * Optional map of wire-encoded `@KsContext` values. When non-null on an input frame,
     * the receiver installs a [com.monkopedia.ksrpc.channels.WireContextMap] so the
     * handler's [com.monkopedia.ksrpc.RpcMethod.call] can decode the values and install
     * them as real coroutine-context elements. Older peers that don't understand this field
     * tolerate it via `ignoreUnknownKeys` on [PACKET_JSON].
     */
    @SerialName("cx")
    val contextMap: Map<String, String>? = null
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

    /**
     * Convenience: true iff this frame represents an error response (i.e. [errorCode] is set).
     */
    val isError: Boolean get() = errorCode != null

    constructor(
        input: Boolean = false,
        binary: Boolean = false,
        startBinary: Boolean = false,
        cancel: Boolean = false,
        id: String,
        messageId: String,
        endpoint: String,
        data: T,
        errorCode: Int? = null,
        errorMessage: String? = null,
        contextMap: Map<String, String>? = null
    ) : this(
        (if (input) 1 else 0) or
            (if (binary) 2 else 0) or
            (if (startBinary) 4 else 0) or
            (if (cancel) 8 else 0),
        id,
        messageId,
        endpoint,
        data,
        errorCode,
        errorMessage,
        contextMap
    )
}

@KsrpcInternal
internal interface PacketChannel<T> : SuspendCloseable {
    suspend fun sendLocked(packet: Packet<T>)
    suspend fun receiveLocked(): Packet<T>
}
