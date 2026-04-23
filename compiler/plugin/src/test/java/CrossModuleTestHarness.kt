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
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

/**
 * Harness for two-stage `KotlinCompilation` tests that exercise the compiler plugin
 * across a "library" module and a downstream "consumer" module (issue #54).
 *
 * The library module compiles first with the ksrpc compiler plugin applied, producing
 * `@KsService` interfaces plus their plugin-generated companion/Stub/RpcObject bodies.
 * The consumer module compiles second, also with the plugin applied, with the library's
 * `classesDir` prepended to its classpath — mirroring how a real downstream KMP module
 * sees a dependency module's compiled output.
 *
 * This exercises the code paths that behave differently for already-compiled binaries
 * (e.g. FIR-phase `getCallableNamesForClass` doesn't fire for types read off the
 * classpath, but the consumer's IR-phase stub/obj generation still has to resolve the
 * companion emitted into the library's classfiles).
 */
object CrossModuleTestHarness {

    /**
     * Compile [librarySources] with the ksrpc plugin applied. Returns the compilation
     * result; callers typically assert `exitCode == OK` and pass the result into
     * [compileConsumer] to stitch its `classesDir` onto a downstream compilation.
     */
    fun compileLibrary(librarySources: List<SourceFile>): JvmCompilationResult =
        KotlinCompilation().apply {
            sources = librarySources
            compilerPluginRegistrars = listOf(KsrpcComponentRegistrar())
            inheritClassPath = true
        }.compile()

    /** Convenience overload for a single-source library module. */
    fun compileLibrary(librarySource: SourceFile): JvmCompilationResult =
        compileLibrary(listOf(librarySource))

    /**
     * Compile [consumerSources] with the ksrpc plugin applied, with
     * [libraryResult].classesDir prepended to the classpath so the consumer sees the
     * library's compiled classes (including the plugin-generated companions and Stubs).
     *
     * Uses `inheritClassPath = true` so the test-runtime classpath (ksrpc-core,
     * kotlinx.serialization, etc.) is still available to the consumer; only the
     * library's classfiles are added on top via [KotlinCompilation.classpaths].
     */
    fun compileConsumer(
        libraryResult: JvmCompilationResult,
        consumerSources: List<SourceFile>
    ): JvmCompilationResult = KotlinCompilation().apply {
        sources = consumerSources
        compilerPluginRegistrars = listOf(KsrpcComponentRegistrar())
        inheritClassPath = true
        classpaths = listOf(libraryResult.outputDirectory) + classpaths
    }.compile()

    /** Convenience overload for a single-source consumer module. */
    fun compileConsumer(
        libraryResult: JvmCompilationResult,
        consumerSource: SourceFile
    ): JvmCompilationResult = compileConsumer(libraryResult, listOf(consumerSource))
}
