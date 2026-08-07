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

internal const val METHOD = "Method"
