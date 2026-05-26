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
import kotlin.test.assertTrue
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Test

/**
 * Codegen + FIR coverage for `Result<O>` return types (issue #133). The plugin
 * wraps the inner-`O` transformer in `ResultTransformer<O>`; FIR rejects the
 * v1-unsupported nested shapes (`Result<Flow<…>>`, `Flow<Result<…>>`,
 * `Result<RpcService-subtype>`, nested `Result<Result<…>>`).
 */
class ResultReturnTypeTest {

    @Test
    fun `Result of serializable value compiles`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService
import kotlinx.serialization.Serializable

@Serializable
data class Out(val v: Int)

@KsService
interface ResultService : RpcService {
    @KsMethod("/r")
    suspend fun r(input: String): Result<Out>
}
"""
        )
        val result = compile(listOf(source))
        assertTrue(
            result.exitCode == KotlinCompilation.ExitCode.OK,
            "Expected Result<Out> to compile; messages: ${result.messages}"
        )
    }

    @Test
    fun `Result of primitive with KsError compiles`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsError
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService
import kotlinx.serialization.Serializable

@Serializable
class MyError(val reason: String) : RuntimeException()

@KsService
interface ResultService : RpcService {
    @KsMethod("/r")
    @KsError(code = 42, type = MyError::class)
    suspend fun r(input: String): Result<Int>
}
"""
        )
        val result = compile(listOf(source))
        assertTrue(
            result.exitCode == KotlinCompilation.ExitCode.OK,
            "Expected Result<Int> with @KsError to compile; messages: ${result.messages}"
        )
    }

    @Test
    fun `nested Result is rejected`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

@KsService
interface BadService : RpcService {
    @KsMethod("/r")
    suspend fun r(input: String): Result<Result<Int>>
}
"""
        )
        val result = compile(listOf(source))
        assertTrue(
            result.exitCode != KotlinCompilation.ExitCode.OK,
            "Expected compilation to fail; messages: ${result.messages}"
        )
        assertTrue(
            result.messages.contains("nested Result"),
            "Expected nested-Result diagnostic, got: ${result.messages}"
        )
    }

    @Test
    fun `Result of Flow is rejected`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService
import kotlinx.coroutines.flow.Flow

@KsService
interface BadService : RpcService {
    @KsMethod("/r")
    suspend fun r(input: String): Result<Flow<Int>>
}
"""
        )
        val result = compile(listOf(source))
        assertTrue(
            result.exitCode != KotlinCompilation.ExitCode.OK,
            "Expected compilation to fail; messages: ${result.messages}"
        )
        assertTrue(
            result.messages.contains("Result<Flow"),
            "Expected Result<Flow> diagnostic, got: ${result.messages}"
        )
    }

    @Test
    fun `Flow of Result is rejected`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService
import kotlinx.coroutines.flow.Flow

@KsService
interface BadService : RpcService {
    @KsMethod("/r")
    suspend fun r(input: String): Flow<Result<Int>>
}
"""
        )
        val result = compile(listOf(source))
        assertTrue(
            result.exitCode != KotlinCompilation.ExitCode.OK,
            "Expected compilation to fail; messages: ${result.messages}"
        )
        assertTrue(
            result.messages.contains("Flow<Result"),
            "Expected Flow<Result> diagnostic, got: ${result.messages}"
        )
    }

    @Test
    fun `Result of subservice is rejected`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

@KsService
interface SubService : RpcService {
    @KsMethod("/x")
    suspend fun x(input: String): String
}

@KsService
interface BadService : RpcService {
    @KsMethod("/r")
    suspend fun r(input: String): Result<SubService>
}
"""
        )
        val result = compile(listOf(source))
        assertTrue(
            result.exitCode != KotlinCompilation.ExitCode.OK,
            "Expected compilation to fail; messages: ${result.messages}"
        )
        assertTrue(
            result.messages.contains("@KsService sub-service"),
            "Expected Result<@KsService> diagnostic, got: ${result.messages}"
        )
    }
}
