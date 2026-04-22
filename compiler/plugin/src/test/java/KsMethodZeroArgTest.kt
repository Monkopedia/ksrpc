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
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Test

/**
 * Compile-only coverage for issue #40: `@KsMethod` functions should be accepted with
 * zero value parameters. The generated `RpcMethod` uses `Unit` as the input type in
 * that case, so mixing 0-arg and 1-arg methods on the same service must compile.
 */
class KsMethodZeroArgTest {

    @Test
    fun `zero arg KsMethod compiles`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

@KsService
interface ZeroArgService : RpcService {
    @KsMethod("/ping")
    suspend fun ping(): String
}
"""
        )
        val result = compile(listOf(source))
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "Expected compilation to succeed; messages: ${result.messages}"
        )
    }

    @Test
    fun `zero arg and one arg methods coexist`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

@KsService
interface MixedService : RpcService {
    @KsMethod("/ping")
    suspend fun ping(): String

    @KsMethod("/greet")
    suspend fun greet(name: String): String
}
"""
        )
        val result = compile(listOf(source))
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "Expected compilation to succeed; messages: ${result.messages}"
        )
    }

    @Test
    fun `zero arg with explicit Unit still compiles for backward compat`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

@KsService
interface ExplicitUnitService : RpcService {
    @KsMethod("/ping")
    suspend fun ping(u: Unit): String
}
"""
        )
        val result = compile(listOf(source))
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "Expected compilation to succeed; messages: ${result.messages}"
        )
    }

    @Test
    fun `zero arg on generic service compiles`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

@KsService
interface GenericZeroArg<T> : RpcService {
    @KsMethod("/get")
    suspend fun get(): T
}
"""
        )
        val result = compile(listOf(source))
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "Expected compilation to succeed; messages: ${result.messages}"
        )
    }
}
