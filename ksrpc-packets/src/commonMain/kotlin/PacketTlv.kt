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

/**
 * Reserved tag numbers for the [Packet.metadata] TLV section (see issue #70).
 *
 * Tags are 16-bit unsigned values carried in a map keyed by [Int]. Allocations happen here
 * from 1 upward; tag 0 is reserved as "unassigned". Implementations MUST silently ignore
 * tags they do not recognise — this is the forward-compatibility contract that the TLV
 * section exists to guarantee.
 *
 * Adding a new tag is NOT a wire break provided older peers leave it in place (or drop it)
 * without failing. Removing a tag IS a break if in-flight peers require it.
 */
object PacketTlv {
    /** Reserved for #28 context propagation. Value encoding: consumer-defined. */
    const val CONTEXT_MAP: Int = 1

    /** Largest valid tag value (16-bit unsigned). */
    const val MAX_TAG: Int = 0xFFFF
}
