/**
 * Copyright (C) 2025 Jason Monk <monkopedia@gmail.com>
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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    val data: T
) {
    val input: Boolean get() = (type and 1) != 0
    val binary: Boolean get() = (type and 2) != 0
    val startBinary: Boolean get() = (type and 4) != 0

    constructor(
        input: Boolean = false,
        binary: Boolean = false,
        startBinary: Boolean = false,
        id: String,
        messageId: String,
        endpoint: String,
        data: T
    ) : this(
        (if (input) 1 else 0) or
            (if (binary) 2 else 0) or
            (if (startBinary) 4 else 0),
        id,
        messageId,
        endpoint,
        data
    )
}

internal interface PacketChannel<T> : SuspendCloseable {
    suspend fun sendLocked(packet: Packet<T>)
    suspend fun receiveLocked(): Packet<T>
}
