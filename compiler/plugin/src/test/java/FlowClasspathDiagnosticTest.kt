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
@file:OptIn(ExperimentalCompilerApi::class)

package com.monkopedia.ksrpc.plugin

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import java.io.File
import kotlin.test.assertTrue
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Test

/**
 * Covers the classpath-missing diagnostic emitted by
 * [KsrpcIrGenerationExtension.validateMethod] when a service uses
 * `kotlinx.coroutines.flow.Flow` in a `@KsMethod` signature without
 * `ksrpc-flow` on the compile classpath (issue #67).
 *
 * Without this diagnostic, the compilation later fails with the cryptic
 * `Serializer for class 'Flow' not found` error from kotlinx.serialization
 * because `determineType` falls through to `RpcType.DEFAULT` when
 * `flowSupported=false`. Mirrors the pattern used by
 * [BinaryAdapterDiagnosticTest] for the ksrpc-binary-* adapter modules.
 */
class FlowClasspathDiagnosticTest {

    @Test
    fun `Flow return without ksrpc-flow reports missing-module diagnostic`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcBidiService
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
data class Update(val id: String)

@KsService
interface MyService : RpcBidiService {
    @KsMethod("/updates")
    suspend fun updates(filter: String): Flow<Update>
}
"""
        )
        val result = compileWithoutFlow(source)
        assertTrue(
            result.exitCode != KotlinCompilation.ExitCode.OK,
            "Expected compilation to fail; messages: ${result.messages}"
        )
        assertTrue(
            result.messages.contains("kotlinx.coroutines.flow.Flow") &&
                result.messages.contains("ksrpc-flow") &&
                result.messages.contains("not on the compile classpath"),
            "Expected classpath-missing diagnostic naming Flow and ksrpc-flow, " +
                "got: ${result.messages}"
        )
    }

    @Test
    fun `Flow parameter without ksrpc-flow reports missing-module diagnostic`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcBidiService
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
data class Chunk(val bytes: String)

@Serializable
data class Receipt(val ok: Boolean)

@KsService
interface UploadService : RpcBidiService {
    @KsMethod("/upload")
    suspend fun upload(items: Flow<Chunk>): Receipt
}
"""
        )
        val result = compileWithoutFlow(source)
        assertTrue(
            result.exitCode != KotlinCompilation.ExitCode.OK,
            "Expected compilation to fail; messages: ${result.messages}"
        )
        assertTrue(
            result.messages.contains("kotlinx.coroutines.flow.Flow") &&
                result.messages.contains("ksrpc-flow") &&
                result.messages.contains("not on the compile classpath"),
            "Expected classpath-missing diagnostic naming Flow and ksrpc-flow, " +
                "got: ${result.messages}"
        )
    }

    /**
     * Compile [source] with the ksrpc compiler plugin, but strip every classpath
     * entry whose path contains `ksrpc-flow` so the compiler cannot resolve
     * `KsFlowService`/`FlowTransformer`. `kotlinx.coroutines.flow.Flow` itself
     * remains resolvable because the kotlinx-coroutines jar stays on the
     * classpath — only the `ksrpc-flow` adapter jar is removed.
     */
    private fun compileWithoutFlow(source: SourceFile): JvmCompilationResult {
        val filteredClasspath = System.getProperty("java.class.path")
            .split(File.pathSeparatorChar)
            .map(::File)
            .filter { entry -> !entry.absolutePath.contains("ksrpc-flow") }
        return KotlinCompilation().apply {
            sources = listOf(source)
            compilerPluginRegistrars = listOf(KsrpcComponentRegistrar())
            inheritClassPath = false
            classpaths = filteredClasspath
        }.compile()
    }
}
