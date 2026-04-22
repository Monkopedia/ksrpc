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
package com.monkopedia.ksrpc.channels

import com.monkopedia.ksrpc.SuspendCloseable

/**
 * Transport-agnostic binary data source. Implementations adapt user-facing
 * types (`ByteReadChannel`, `kotlinx.io.Source`, `okio.BufferedSource`, ...)
 * to a shape the ksrpc wire protocol can consume.
 *
 * Source-oriented only: in ksrpc the binary payload is always "something the
 * receiver reads from," regardless of which side is sending.
 */
interface RpcBinaryData : SuspendCloseable {
    /** Known byte length, or `null` for unknown / streaming sources. */
    val size: Long?
        get() = null

    /**
     * Pull all bytes from this source into [sink]. The [sink] callback may be
     * invoked many times with different offsets / lengths into a buffer the
     * implementation owns; callers must not retain the `bytes` array beyond
     * the duration of the call.
     */
    suspend fun transferTo(sink: suspend (bytes: ByteArray, offset: Int, length: Int) -> Unit)
}
