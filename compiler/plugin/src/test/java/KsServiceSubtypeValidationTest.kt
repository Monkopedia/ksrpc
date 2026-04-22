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

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Test

/**
 * Regression tests for issue #45: applying `@KsService` to a subtype of another
 * `@KsService` interface used to crash CompanionGeneration with
 * "Invalid synthetic declaration for ...". The plugin now rejects this with a clear
 * compile-time diagnostic, while the supported workaround (a plain Kotlin subtype with
 * no `@KsService`) continues to work — see `GenericServiceSubtypeJvmTest`.
 */
class KsServiceSubtypeValidationTest {

    @Test
    fun `KsService on subtype of generic KsService is rejected with a clear diagnostic`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

@KsService
interface GenericEcho<T> : RpcService {
    @KsMethod("/echo")
    suspend fun echo(item: T): T
}

@KsService
interface TypedGenericEcho : GenericEcho<String>
"""
        )
        val result = compile(listOf(source))
        assertTrue(
            result.exitCode != KotlinCompilation.ExitCode.OK,
            "Expected compilation to fail but got: ${result.exitCode}\n${result.messages}"
        )
        assertTrue(
            result.messages.contains("@KsService cannot be applied to") &&
                result.messages.contains("TypedGenericEcho") &&
                result.messages.contains("GenericEcho"),
            "Expected diagnostic mentioning @KsService, TypedGenericEcho and GenericEcho, " +
                "got: ${result.messages}"
        )
        // Make sure we're not seeing the old crash anymore.
        assertTrue(
            !result.messages.contains("Invalid synthetic declaration"),
            "Plugin should not crash with 'Invalid synthetic declaration' anymore, " +
                "got: ${result.messages}"
        )
    }

    @Test
    fun `KsService on subtype of non-generic KsService is rejected`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

@KsService
interface ParentService : RpcService {
    @KsMethod("/op")
    suspend fun op(input: String): String
}

@KsService
interface ChildService : ParentService
"""
        )
        val result = compile(listOf(source))
        assertTrue(
            result.exitCode != KotlinCompilation.ExitCode.OK,
            "Expected compilation to fail but got: ${result.exitCode}\n${result.messages}"
        )
        assertTrue(
            result.messages.contains("@KsService cannot be applied to") &&
                result.messages.contains("ChildService") &&
                result.messages.contains("ParentService"),
            "Expected diagnostic mentioning @KsService, ChildService and ParentService, " +
                "got: ${result.messages}"
        )
    }

    @Test
    fun `KsService directly extending RpcService still compiles`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

@KsService
interface Unrelated : RpcService {
    @KsMethod("/op")
    suspend fun op(input: String): String
}
"""
        )
        val result = compile(listOf(source))
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "Expected compilation to succeed, got: ${result.messages}"
        )
    }

    @Test
    fun `Plain Kotlin subtype of generic KsService still compiles`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

@KsService
interface GenericEcho<T> : RpcService {
    @KsMethod("/echo")
    suspend fun echo(item: T): T
}

// No @KsService — this is the supported pattern for specializing a generic service.
interface TypedGenericEcho : GenericEcho<String>
"""
        )
        val result = compile(listOf(source))
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "Expected compilation to succeed, got: ${result.messages}"
        )
    }
}
