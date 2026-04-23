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
 * [KsrpcIrGenerationExtension.validateMethod] when a service uses a known binary
 * adapter type (ByteReadChannel, kotlinx.io.Source, okio.BufferedSource) without
 * the matching `ksrpc-binary-*` adapter module on the compile classpath.
 *
 * Each test compiles a service that references the offending binary type with
 * its adapter module stripped from the test classpath, and asserts that the
 * plugin fails compilation with a message naming both the user-facing type FQN
 * and the adapter module hint.
 *
 * Also covers the happy path: a service that uses all three binary types
 * simultaneously with every adapter module present on the classpath compiles.
 */
class BinaryAdapterDiagnosticTest {

    @Test
    fun `ByteReadChannel without ksrpc-binary-ktor reports missing-adapter diagnostic`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService
import io.ktor.utils.io.ByteReadChannel

@KsService
interface BinaryService : RpcService {
    @KsMethod("/download")
    suspend fun download(name: String): ByteReadChannel
}
"""
        )
        val result = compileWithoutAdapter(source, excludeJarSubstring = "ksrpc-binary-ktor")
        assertTrue(
            result.exitCode != KotlinCompilation.ExitCode.OK,
            "Expected compilation to fail; messages: ${result.messages}"
        )
        assertTrue(
            result.messages.contains("io.ktor.utils.io.ByteReadChannel") &&
                result.messages.contains("ksrpc-binary-ktor") &&
                result.messages.contains("not on the compile classpath"),
            "Expected classpath-missing diagnostic naming ByteReadChannel and " +
                "ksrpc-binary-ktor, got: ${result.messages}"
        )
    }

    @Test
    fun `kotlinx_io Source without ksrpc-binary-kotlinx-io reports missing-adapter diagnostic`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService
import kotlinx.io.Source

@KsService
interface BinaryService : RpcService {
    @KsMethod("/download")
    suspend fun download(name: String): Source
}
"""
        )
        val result = compileWithoutAdapter(source, excludeJarSubstring = "ksrpc-binary-kotlinx-io")
        assertTrue(
            result.exitCode != KotlinCompilation.ExitCode.OK,
            "Expected compilation to fail; messages: ${result.messages}"
        )
        assertTrue(
            result.messages.contains("kotlinx.io.Source") &&
                result.messages.contains("ksrpc-binary-kotlinx-io") &&
                result.messages.contains("not on the compile classpath"),
            "Expected classpath-missing diagnostic naming kotlinx.io.Source and " +
                "ksrpc-binary-kotlinx-io, got: ${result.messages}"
        )
    }

    @Test
    fun `okio BufferedSource without ksrpc-binary-okio reports missing-adapter diagnostic`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService
import okio.BufferedSource

@KsService
interface BinaryService : RpcService {
    @KsMethod("/download")
    suspend fun download(name: String): BufferedSource
}
"""
        )
        val result = compileWithoutAdapter(source, excludeJarSubstring = "ksrpc-binary-okio")
        assertTrue(
            result.exitCode != KotlinCompilation.ExitCode.OK,
            "Expected compilation to fail; messages: ${result.messages}"
        )
        assertTrue(
            result.messages.contains("okio.BufferedSource") &&
                result.messages.contains("ksrpc-binary-okio") &&
                result.messages.contains("not on the compile classpath"),
            "Expected classpath-missing diagnostic naming okio.BufferedSource and " +
                "ksrpc-binary-okio, got: ${result.messages}"
        )
    }

    @Test
    fun `service mixing all three binary types compiles when every adapter is present`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService
import io.ktor.utils.io.ByteReadChannel
import kotlinx.io.Source
import okio.BufferedSource

@KsService
interface BinaryService : RpcService {
    @KsMethod("/ktor")
    suspend fun ktor(name: String): ByteReadChannel

    @KsMethod("/kxio")
    suspend fun kxio(name: String): Source

    @KsMethod("/okio")
    suspend fun okio(name: String): BufferedSource
}
"""
        )
        val result = compile(sourceFile = source)
        assertTrue(
            result.exitCode == KotlinCompilation.ExitCode.OK,
            "Expected compilation to succeed when all adapter modules are present; " +
                "messages: ${result.messages}"
        )
    }

    /**
     * Compile [source] with the ksrpc compiler plugin, but strip every classpath
     * entry whose path contains [excludeJarSubstring] so the compiler cannot
     * resolve classes from the excluded adapter module. The user-facing type
     * (e.g. `io.ktor.utils.io.ByteReadChannel`) is resolvable because the
     * ktor-io jar remains on the classpath transitively via `ksrpc-core` or
     * `ksrpc-packets` — only the `ksrpc-binary-*` jar containing the Transformer
     * is removed.
     */
    private fun compileWithoutAdapter(
        source: SourceFile,
        excludeJarSubstring: String
    ): JvmCompilationResult {
        val filteredClasspath = System.getProperty("java.class.path")
            .split(File.pathSeparatorChar)
            .map(::File)
            .filter { entry -> !entry.absolutePath.contains(excludeJarSubstring) }
        return KotlinCompilation().apply {
            sources = listOf(source)
            compilerPluginRegistrars = listOf(KsrpcComponentRegistrar())
            inheritClassPath = false
            classpaths = filteredClasspath
        }.compile()
    }
}
