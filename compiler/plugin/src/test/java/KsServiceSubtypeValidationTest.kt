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
 * Tests for @KsService interface inheritance support.
 *
 * @KsService on a subtype of another @KsService is now allowed (interface hierarchy).
 * Only true diamond inheritance (two UNRELATED @KsService ancestors) is rejected.
 * The supported workaround (a plain Kotlin subtype with no `@KsService`) also
 * continues to work — see `GenericServiceSubtypeJvmTest`.
 */
class KsServiceSubtypeValidationTest {

    @Test
    fun `KsService on subtype of generic KsService compiles successfully`() {
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
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "Expected @KsService on subtype of generic @KsService to compile. " +
                "messages: ${result.messages}"
        )
    }

    @Test
    fun `KsService on subtype of non-generic KsService compiles successfully`() {
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
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "Expected @KsService on subtype of non-generic @KsService to compile. " +
                "messages: ${result.messages}"
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
