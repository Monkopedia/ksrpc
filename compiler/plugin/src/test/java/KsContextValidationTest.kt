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
 * Compile-time coverage for the `@KsContext` core API and its FIR-phase
 * validation (issue #80, part 1 of #28).
 *
 * The plugin tests run against an included-build classpath that pins the
 * published `ksrpc-api` coordinate alongside the locally-built jvmJar
 * (build.gradle.kts in :compiler:ksrpc-compiler-plugin). To keep this test
 * self-contained — and to keep the published 0.11.1 coordinate from gating
 * the test — we re-declare the relevant ksrpc public symbols inline. Only
 * the FQ names matter to the FIR checker.
 */
class KsContextValidationTest {

    private val ksContextSurface = SourceFile.kotlin(
        "KsContextSurface.kt",
        """
package com.monkopedia.ksrpc.annotation

import com.monkopedia.ksrpc.KsContextBinding
import kotlin.reflect.KClass

@Target(AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class KsContext(val binding: KClass<out KsContextBinding<*>>)
"""
    )

    private val ksContextBinding = SourceFile.kotlin(
        "KsContextBinding.kt",
        """
package com.monkopedia.ksrpc

import kotlin.coroutines.CoroutineContext

interface KsContextBinding<T : CoroutineContext.Element> : CoroutineContext.Key<T> {
    val wireKey: String
    fun toWire(value: T): String
    fun fromWire(encoded: String): T
}
"""
    )

    private val supportSources = listOf(ksContextSurface, ksContextBinding)

    @Test
    fun `@KsContext annotation with valid binding compiles cleanly on a method`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsContext
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.KsContextBinding
import com.monkopedia.ksrpc.RpcService
import kotlin.coroutines.CoroutineContext

class TraceId(val value: String) : CoroutineContext.Element {
    override val key get() = Key
    companion object Key : KsContextBinding<TraceId> {
        override val wireKey: String = "x-trace-id"
        override fun toWire(value: TraceId): String = value.value
        override fun fromWire(encoded: String): TraceId = TraceId(encoded)
    }
}

@KsContext(binding = TraceId.Key::class)
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class WithTrace

@KsService
interface MyService : RpcService {
    @KsMethod("/op")
    @WithTrace
    suspend fun op(input: String): String
}
"""
        )
        val result = compile(supportSources + source)
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "Expected @KsContext with a valid binding to compile; messages: ${result.messages}"
        )
    }

    @Test
    fun `@KsContext annotation with valid binding compiles cleanly on a service`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsContext
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.KsContextBinding
import com.monkopedia.ksrpc.RpcService
import kotlin.coroutines.CoroutineContext

class TraceId(val value: String) : CoroutineContext.Element {
    override val key get() = Key
    companion object Key : KsContextBinding<TraceId> {
        override val wireKey: String = "x-trace-id"
        override fun toWire(value: TraceId): String = value.value
        override fun fromWire(encoded: String): TraceId = TraceId(encoded)
    }
}

@KsContext(binding = TraceId.Key::class)
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class WithTrace

@WithTrace
@KsService
interface MyService : RpcService {
    @KsMethod("/op")
    suspend fun op(input: String): String
}
"""
        )
        val result = compile(supportSources + source)
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "Expected @KsContext on a @KsService to compile; messages: ${result.messages}"
        )
    }

    @Test
    fun `@KsContext binding that does not implement KsContextBinding is rejected`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsContext
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

class NotABinding

@KsContext(binding = NotABinding::class)
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class WithBogus

@KsService
interface MyService : RpcService {
    @KsMethod("/op")
    @WithBogus
    suspend fun op(input: String): String
}
"""
        )
        val result = compile(supportSources + source)
        assertTrue(
            result.exitCode != KotlinCompilation.ExitCode.OK,
            "Expected compilation to fail; messages: ${result.messages}"
        )
        assertTrue(
            result.messages.contains("KsContextBinding") &&
                result.messages.contains("NotABinding"),
            "Expected diagnostic naming KsContextBinding and the offending class; " +
                "got: ${result.messages}"
        )
    }

    @Test
    fun `two @KsContext annotations with the same wireKey on one method is rejected`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsContext
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.KsContextBinding
import com.monkopedia.ksrpc.RpcService
import kotlin.coroutines.CoroutineContext

class TraceId(val value: String) : CoroutineContext.Element {
    override val key get() = Key
    companion object Key : KsContextBinding<TraceId> {
        override val wireKey: String = "x-shared"
        override fun toWire(value: TraceId): String = value.value
        override fun fromWire(encoded: String): TraceId = TraceId(encoded)
    }
}
class SpanId(val value: String) : CoroutineContext.Element {
    override val key get() = Key
    companion object Key : KsContextBinding<SpanId> {
        override val wireKey: String = "x-shared"
        override fun toWire(value: SpanId): String = value.value
        override fun fromWire(encoded: String): SpanId = SpanId(encoded)
    }
}

@KsContext(binding = TraceId.Key::class)
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class WithTrace

@KsContext(binding = SpanId.Key::class)
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class WithSpan

@KsService
interface MyService : RpcService {
    @KsMethod("/op")
    @WithTrace
    @WithSpan
    suspend fun op(input: String): String
}
"""
        )
        val result = compile(supportSources + source)
        assertTrue(
            result.exitCode != KotlinCompilation.ExitCode.OK,
            "Expected compilation to fail; messages: ${result.messages}"
        )
        assertTrue(
            result.messages.contains("duplicate wireKey") &&
                result.messages.contains("\"x-shared\""),
            "Expected duplicate-wireKey diagnostic naming the shared key; " +
                "got: ${result.messages}"
        )
    }
}
