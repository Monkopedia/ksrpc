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

import com.monkopedia.ksrpc.annotation.KsrpcInternal
import kotlinx.serialization.json.Json

/**
 * Json instance used for serializing the [Packet] wire envelope. Configured with
 * `ignoreUnknownKeys = true` so that new optional fields on [Packet] in future releases
 * don't break peers compiled against the older schema.
 *
 * This is intentionally internal to ksrpc-packets — the user's configured
 * [com.monkopedia.ksrpc.CallDataSerializer] controls serialization of user-declared
 * method inputs/outputs, but the packet envelope itself is ksrpc's wire concern.
 */
@KsrpcInternal
val PACKET_JSON: Json = Json {
    ignoreUnknownKeys = true
    // Packet has nullable optional fields (ec, em, cx) that default to null.
    // Explicitly skip them during encoding to avoid wire bloat and reduce
    // serialization overhead — this is the default but stated for clarity.
    encodeDefaults = false
}
