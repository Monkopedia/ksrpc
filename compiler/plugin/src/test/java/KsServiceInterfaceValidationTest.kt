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
 * Regression tests for issue #53: `@KsService` applied to a class (abstract or concrete),
 * object, enum, or annotation type used to compile without any diagnostic, even though the
 * generated companion/Stub code only works for interfaces. The plugin now rejects these
 * with a clear compile-time error pointing at the offending declaration.
 */
class KsServiceInterfaceValidationTest {

    @Test
    fun `KsService on an interface still compiles`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

@KsService
interface Foo : RpcService {
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
    fun `KsService on an abstract class is rejected`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

@KsService
abstract class Foo : RpcService {
    @KsMethod("/op")
    abstract suspend fun op(input: String): String
}
"""
        )
        val result = compile(listOf(source))
        assertTrue(
            result.exitCode != KotlinCompilation.ExitCode.OK,
            "Expected compilation to fail but got: ${result.exitCode}\n${result.messages}"
        )
        assertTrue(
            result.messages.contains(
                "@KsService can only be applied to interfaces"
            ) && result.messages.contains("Foo"),
            "Expected diagnostic mentioning @KsService interface-only rule and Foo, " +
                "got: ${result.messages}"
        )
    }

    @Test
    fun `KsService on a concrete class is rejected`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

@KsService
class Foo : RpcService {
    @KsMethod("/op")
    suspend fun op(input: String): String = input
}
"""
        )
        val result = compile(listOf(source))
        assertTrue(
            result.exitCode != KotlinCompilation.ExitCode.OK,
            "Expected compilation to fail but got: ${result.exitCode}\n${result.messages}"
        )
        assertTrue(
            result.messages.contains(
                "@KsService can only be applied to interfaces"
            ) && result.messages.contains("Foo"),
            "Expected diagnostic mentioning @KsService interface-only rule and Foo, " +
                "got: ${result.messages}"
        )
    }

    @Test
    fun `KsService on an object is rejected`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

@KsService
object Foo : RpcService {
    @KsMethod("/op")
    suspend fun op(input: String): String = input
}
"""
        )
        val result = compile(listOf(source))
        assertTrue(
            result.exitCode != KotlinCompilation.ExitCode.OK,
            "Expected compilation to fail but got: ${result.exitCode}\n${result.messages}"
        )
        assertTrue(
            result.messages.contains(
                "@KsService can only be applied to interfaces"
            ) && result.messages.contains("Foo"),
            "Expected diagnostic mentioning @KsService interface-only rule and Foo, " +
                "got: ${result.messages}"
        )
    }
}
