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

class GenericServiceTest {

    @Test
    fun `generic service Stub is parameterized with serializer`() {
        // The generated `Stub` class for a generic service picks up one type parameter per
        // service type parameter, and its primary constructor takes one KSerializer<T> per
        // type parameter in addition to the channel. This test verifies the FIR-side shape
        // of the Stub is usable from source.
        //
        // NOTE: The IR-side body generation for generic stubs does not yet route the
        // injected KSerializer<T> into method transforms — that is follow-up work tracked
        // on #41. Invoking stub methods whose signatures reference T will fail at runtime
        // until that work lands.
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService
import com.monkopedia.ksrpc.channels.SerializedService
import kotlinx.serialization.KSerializer

@KsService
interface KsStream<T>: RpcService {
    @KsMethod("/next")
    suspend fun next(u: Unit): T
}

fun <S> useStub(channel: SerializedService<S>, s: KSerializer<String>): KsStream<String> =
    KsStream.Stub<String>(channel, s)
"""
        )
        val result = compile(sourceFile = source)
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "messages: ${result.messages}"
        )
    }

    @Test
    fun `generic ksservice with T return compiles`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

@KsService
interface KsStream<T>: RpcService {
    @KsMethod("/next")
    suspend fun next(u: Unit): T
}
"""
        )
        val result = compile(sourceFile = source)
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "messages: ${result.messages}"
        )
    }

    @Test
    fun `generic ksservice with T param compiles`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

@KsService
interface KsSink<T>: RpcService {
    @KsMethod("/push")
    suspend fun push(item: T)
}
"""
        )
        val result = compile(sourceFile = source)
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "messages: ${result.messages}"
        )
    }

    @Test
    fun `generic ksservice with nullable T compiles`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

@KsService
interface KsMaybe<T>: RpcService {
    @KsMethod("/peek")
    suspend fun peek(u: Unit): T?
}
"""
        )
        val result = compile(sourceFile = source)
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "messages: ${result.messages}"
        )
    }

    @Test
    fun `generic ksservice with List of T compiles`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

@KsService
interface KsBatcher<T>: RpcService {
    @KsMethod("/batch")
    suspend fun batch(items: List<T>): Int
}
"""
        )
        val result = compile(sourceFile = source)
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "messages: ${result.messages}"
        )
    }

    @Test
    fun `generic ksservice with List-of-T input and output compiles`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

@KsService
interface KsListEcho<T>: RpcService {
    @KsMethod("/echo")
    suspend fun echo(items: List<T>): List<T>
}
"""
        )
        val result = compile(sourceFile = source)
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "messages: ${result.messages}"
        )
    }

    @Test
    fun `generic ksservice with Set of T compiles`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

@KsService
interface KsSetEcho<T>: RpcService {
    @KsMethod("/echo")
    suspend fun echo(items: Set<T>): Set<T>
}
"""
        )
        val result = compile(sourceFile = source)
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "messages: ${result.messages}"
        )
    }

    @Test
    fun `generic ksservice with Map of String to T compiles`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

@KsService
interface KsMapEcho<T>: RpcService {
    @KsMethod("/echo")
    suspend fun echo(entries: Map<String, T>): Map<String, T>
}
"""
        )
        val result = compile(sourceFile = source)
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "messages: ${result.messages}"
        )
    }

    @Test
    fun `generic ksservice with nullable wrappers compiles`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

@KsService
interface KsNullableWrappers<T>: RpcService {
    @KsMethod("/a")
    suspend fun a(items: List<T>?): List<T>?

    @KsMethod("/b")
    suspend fun b(items: List<T?>): List<T?>

    @KsMethod("/c")
    suspend fun c(entries: Map<String, T?>?): Map<String, T?>?
}
"""
        )
        val result = compile(sourceFile = source)
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "messages: ${result.messages}"
        )
    }

    @Test
    fun `generic ksservice with nested wrappers compiles`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

@KsService
interface KsNested<T>: RpcService {
    @KsMethod("/a")
    suspend fun a(items: List<List<T>>): List<List<T>>

    @KsMethod("/b")
    suspend fun b(entries: Map<String, List<T>>): Map<String, List<T>>
}
"""
        )
        val result = compile(sourceFile = source)
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "messages: ${result.messages}"
        )
    }

    @Test
    fun `method-level type parameters are still rejected`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

@KsService
interface HasMethodGeneric: RpcService {
    @KsMethod("/gen")
    suspend fun <U> gen(u: U): U
}
"""
        )
        val result = compile(sourceFile = source)
        assertTrue(
            result.exitCode != KotlinCompilation.ExitCode.OK,
            "Expected compilation to fail for method-level type params, got: ${result.exitCode}"
        )
        assertTrue(
            result.messages.contains("cannot have type parameters"),
            "Expected error about type parameters, got: ${result.messages}"
        )
    }

    @Test
    fun `out-variance on class type params is rejected`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

@KsService
interface Covariant<out T>: RpcService {
    @KsMethod("/next")
    suspend fun next(u: Unit): T
}
"""
        )
        val result = compile(sourceFile = source)
        assertTrue(
            result.exitCode != KotlinCompilation.ExitCode.OK,
            "Expected compilation to fail for variant type params, got: ${result.exitCode}"
        )
        assertTrue(
            result.messages.contains("must be invariant"),
            "Expected error about variance, got: ${result.messages}"
        )
    }
}
