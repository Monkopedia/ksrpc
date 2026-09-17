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
 * On a length-prefixed packet transport, 64 MiB is four thousand times the 16 KiB
 * a packet channel chunks outbound frames to (`PacketChannelBase.DEFAULT_MAX_SIZE`),
 * so no frame this codebase produces comes near it.
 *
 * **That last paragraph is scoped to packet channels and does not travel with the
 * constant.** It holds for every consumer today, because each one bounds a length the
 * peer declares before any content is read — `readContent` in `ReadWritePacketChannel.kt`,
 * and `JsonRpcHeader.receive`. Both are the shape the argument was written for.
 *
 * It stops holding for a consumer that reads until a terminator rather than to a
 * declared length, or that sits on a transport which does not chunk outbound. For such
 * a caller the constant still caps memory, but the "nothing we produce comes near it"
 * half is about a different transport and says nothing about what a peer may send.
 * Check that before lifting this argument onto a new caller; the number is the same,
 * the question it answers is not.
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
