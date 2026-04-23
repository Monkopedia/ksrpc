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
package com.monkopedia.ksrpc.plugin.fir

import com.monkopedia.ksrpc.plugin.FqConstants
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

/**
 * Static (classpath-free) view of the plugin's binary-adapter registry. Used
 * by FIR-phase checkers which do not have access to `KsrpcGenerationEnvironment`
 * (that object lives in the IR phase and depends on an `IrPluginContext`).
 *
 * The FIR checker verifies adapter presence via the FIR symbol provider rather
 * than a pre-resolved `IrClassSymbol` — see
 * `KsMethodFunctionChecker` for the lookup.
 *
 * Keep in sync with [KsrpcGenerationEnvironment.binaryAdapters] — both derive
 * from the same [FqConstants] entries.
 */
internal object BinaryAdapterRegistry {
    data class Entry(
        val userFqName: FqName,
        val transformerClassId: ClassId,
        val moduleHint: String
    )

    private val adapters: List<Entry> = listOf(
        Entry(
            FqConstants.BYTE_READ_CHANNEL,
            FqConstants.BYTE_READ_CHANNEL_TRANSFORMER,
            "ksrpc-binary-ktor"
        ),
        Entry(
            FqConstants.KOTLINX_IO_SOURCE,
            FqConstants.SOURCE_TRANSFORMER,
            "ksrpc-binary-kotlinx-io"
        ),
        Entry(
            FqConstants.OKIO_BUFFERED_SOURCE,
            FqConstants.BUFFERED_SOURCE_TRANSFORMER,
            "ksrpc-binary-okio"
        )
    )

    private val userFqSet: Set<FqName> = adapters.map { it.userFqName }.toSet()

    fun userFqNames(): Set<FqName> = userFqSet

    fun find(fqName: FqName?): Entry? =
        fqName?.let { name -> adapters.firstOrNull { it.userFqName == name } }
}
