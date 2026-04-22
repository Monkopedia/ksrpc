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
 * Covers user-reachable diagnostics emitted by [KsrpcIrGenerationExtension.validateMethod]
 * and [ServiceClass.visitSimpleFunction]. Each failure mode must fail compilation with a
 * message that names the offending declaration, so that users can tell which method of
 * which class is wrong.
 */
class MethodShapeValidationTest {

    @Test
    fun `method with type parameter is rejected`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

@KsService
interface BadService : RpcService {
    @KsMethod("/generic")
    suspend fun <T> genericOp(input: T): T
}
"""
        )
        val result = compile(listOf(source))
        assertTrue(
            result.exitCode != KotlinCompilation.ExitCode.OK,
            "Expected compilation to fail; messages: ${result.messages}"
        )
        assertTrue(
            result.messages.contains("genericOp") &&
                result.messages.contains("cannot have type parameters"),
            "Expected diagnostic naming genericOp and type-parameter rejection, " +
                "got: ${result.messages}"
        )
    }

    @Test
    fun `method with more than one parameter is rejected`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

@KsService
interface BadService : RpcService {
    @KsMethod("/too_many")
    suspend fun tooMany(a: String, b: String): String
}
"""
        )
        val result = compile(listOf(source))
        assertTrue(
            result.exitCode != KotlinCompilation.ExitCode.OK,
            "Expected compilation to fail; messages: ${result.messages}"
        )
        assertTrue(
            result.messages.contains("tooMany") &&
                result.messages.contains("cannot have more than 1 parameter"),
            "Expected diagnostic naming tooMany and parameter-count rejection, " +
                "got: ${result.messages}"
        )
    }

    @Test
    fun `duplicate endpoints are rejected`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

@KsService
interface BadService : RpcService {
    @KsMethod("/dupe")
    suspend fun one(input: String): String

    @KsMethod("/dupe")
    suspend fun two(input: String): String
}
"""
        )
        val result = compile(listOf(source))
        assertTrue(
            result.exitCode != KotlinCompilation.ExitCode.OK,
            "Expected compilation to fail; messages: ${result.messages}"
        )
        assertTrue(
            result.messages.contains("cannot use endpoint") &&
                result.messages.contains("/dupe") &&
                result.messages.contains("two"),
            "Expected duplicate-endpoint diagnostic naming /dupe and `two`, " +
                "got: ${result.messages}"
        )
    }

    @Test
    fun `KsService on non-RpcService is rejected`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService

@KsService
interface NotAnRpcService {
    @KsMethod("/op")
    suspend fun op(input: String): String
}
"""
        )
        val result = compile(listOf(source))
        assertTrue(
            result.exitCode != KotlinCompilation.ExitCode.OK,
            "Expected compilation to fail; messages: ${result.messages}"
        )
        assertTrue(
            result.messages.contains("NotAnRpcService") &&
                result.messages.contains("does not extend"),
            "Expected RpcService-extension diagnostic naming NotAnRpcService, " +
                "got: ${result.messages}"
        )
    }

    @Test
    fun `non-invariant class type parameter is rejected`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

@KsService
interface Variant<out T> : RpcService {
    @KsMethod("/get")
    suspend fun get(input: String): T
}
"""
        )
        val result = compile(listOf(source))
        assertTrue(
            result.exitCode != KotlinCompilation.ExitCode.OK,
            "Expected compilation to fail; messages: ${result.messages}"
        )
        assertTrue(
            result.messages.contains("Variant") &&
                result.messages.contains("must be invariant"),
            "Expected invariance diagnostic naming Variant, got: ${result.messages}"
        )
    }

    @Test
    fun `KsMethod outside of KsService is rejected`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.RpcService

// Intentionally no @KsService — @KsMethod should not be accepted here.
interface UnmarkedService : RpcService {
    @KsMethod("/op")
    suspend fun op(input: String): String
}
"""
        )
        val result = compile(listOf(source))
        assertTrue(
            result.exitCode != KotlinCompilation.ExitCode.OK,
            "Expected compilation to fail; messages: ${result.messages}"
        )
        assertTrue(
            result.messages.contains("@KsMethod can only be applied") &&
                result.messages.contains("op"),
            "Expected @KsMethod-outside-@KsService diagnostic naming `op`, " +
                "got: ${result.messages}"
        )
    }
}
