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

@KsrpcInternal
const val CONTENT_LENGTH = "Content-Length"

@KsrpcInternal
const val CONTENT_TYPE = "Content-Type"

/**
 * Largest inbound frame a length-prefixed transport will allocate a buffer for.
 *
 * `Content-Length` is supplied by the remote peer and the buffer is allocated
 * before any content is read, so an unbounded value lets one short header cost
 * the host its heap.
 *
 * 64 MiB is four thousand times the 16 KiB a packet channel chunks outbound
 * frames to (`PacketChannelBase.DEFAULT_MAX_SIZE`), so no frame this codebase
 * produces comes near it.
 */
@KsrpcInternal
const val MAX_CONTENT_LENGTH = 64 * 1024 * 1024

/**
 * Largest number of header lines a length-prefixed transport will read before the
 * blank terminator.
 *
 * The header map is built from these lines, so this bounds its entry count too — and
 * it bounds the loop itself, which an entry-count cap alone would not: a peer that
 * repeats one key, or sends lines with no `:` at all, never grows the map but can
 * still read forever.
 *
 * ksrpc sends a handful of fields per packet ([METHOD], [CONTENT_LENGTH],
 * [CONTENT_TYPE] and the channel id), so 128 is far above anything this codebase
 * produces.
 */
@KsrpcInternal
const val MAX_HEADER_LINES = 128

/**
 * Longest single header line a length-prefixed transport will read.
 *
 * Passed to `readUTF8Line`, which without it accumulates a line of any length — so a
 * peer that never sends a newline is bounded only by heap, the same shape as the
 * unbounded [MAX_CONTENT_LENGTH] this sits next to.
 */
@KsrpcInternal
const val MAX_HEADER_LINE_LENGTH = 8 * 1024

internal const val METHOD = "Method"
