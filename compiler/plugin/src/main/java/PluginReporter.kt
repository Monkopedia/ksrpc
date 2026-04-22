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
@file:OptIn(UnsafeDuringIrConstructionAPI::class)

package com.monkopedia.ksrpc.plugin

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity.ERROR
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity.WARNING
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.fileOrNull

/**
 * Conventions for surfacing compile-time failures from the ksrpc compiler plugin.
 *
 * - [reportUserError] is for everything a user can trigger by writing bad service code
 *   (invalid annotations, mismatched shapes, duplicate endpoints, ...). These go through
 *   [MessageCollector] as `ERROR` and are resolved to a file:line:column when an
 *   [IrElement] is supplied so the user sees a clean pointer at the offending declaration.
 * - [reportInternal] is for invariants that callers believe are unreachable. The message
 *   is prefixed with `"ksrpc internal: "` so if it ever does fire the user at least gets
 *   a breadcrumb. Still throws — the compilation cannot continue past these.
 *
 * New plugin code should prefer these helpers over bare `error(...)` / `report(ERROR, ...)`.
 *
 * The IR-phase validation that lives in [KsrpcIrGenerationExtension] is currently the best
 * we can do without duplicating service-shape analysis into a FIR checker; a FIR-phase
 * checker with `KtSourceElement` would give richer IDE diagnostics (red squiggle on the
 * offending element), see the TODO there.
 */
internal fun MessageCollector.reportUserError(
    message: String,
    element: IrElement? = null,
    fileHint: IrFile? = null
) {
    report(ERROR, message, element.toLocation(fileHint))
}

internal fun MessageCollector.reportUserWarning(
    message: String,
    element: IrElement? = null,
    fileHint: IrFile? = null
) {
    report(WARNING, message, element.toLocation(fileHint))
}

/**
 * Raise an internal-invariant failure. The message is prefixed with `"ksrpc internal: "`
 * and passed to `error(...)` — the compilation fails with a thrown exception. Use for
 * should-never-happen branches (e.g. validated-away cases, dependency lookups that the
 * earlier environment bootstrap must have resolved). Prefer [reportUserError] whenever
 * the user can actually trigger the site.
 */
internal fun reportInternal(message: String): Nothing = error("ksrpc internal: $message")

private fun IrElement?.toLocation(fileHint: IrFile?): CompilerMessageSourceLocation? {
    val file = when {
        this is IrDeclaration -> fileOrNull ?: fileHint
        else -> fileHint
    } ?: return null
    val entry = file.fileEntry
    val offset = this?.startOffset?.takeIf { it >= 0 } ?: return CompilerMessageLocation.create(
        entry.name
    )
    // IrFileEntry expects offsets within [0, maxOffset]; guard against synthetic offsets.
    if (offset > entry.maxOffset) {
        return CompilerMessageLocation.create(entry.name)
    }
    val lineAndColumn = entry.getLineAndColumnNumbers(offset)
    // IrFileEntry returns 0-based line/column; CompilerMessageLocation expects 1-based.
    return CompilerMessageLocation.create(
        entry.name,
        lineAndColumn.line + 1,
        lineAndColumn.column + 1,
        null
    )
}
