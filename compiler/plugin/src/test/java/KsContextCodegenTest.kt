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
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Test

/**
 * Tests for #81 — compiler plugin captures `@KsContext`-meta-annotated bindings
 * on `@KsMethod` functions (and enclosing `@KsService` interfaces) and emits a
 * `List<KsContextMapping>` into the generated `RpcMethod` descriptor.
 *
 * Uses the same reflection-based introspection strategy as [KsErrorBindingTest].
 */
class KsContextCodegenTest {

    @Test
    fun `method-level @KsContext binding populates contextBindings`() {
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
        val result = compile(listOf(source))
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "messages: ${result.messages}"
        )
        val bindings = findEndpointContextBindings(result, "MyService", "op")
        assertEquals(1, bindings.size, "Expected exactly one context binding, got $bindings")
        assertEquals("x-trace-id", bindings.single().wireKey)
    }

    @Test
    fun `class-level @KsContext binding populates contextBindings on all methods`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsContext
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.KsContextBinding
import com.monkopedia.ksrpc.RpcService
import kotlin.coroutines.CoroutineContext

class AuthToken(val token: String) : CoroutineContext.Element {
    override val key get() = Key
    companion object Key : KsContextBinding<AuthToken> {
        override val wireKey: String = "x-auth-token"
        override fun toWire(value: AuthToken): String = value.token
        override fun fromWire(encoded: String): AuthToken = AuthToken(encoded)
    }
}

@KsContext(binding = AuthToken.Key::class)
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class WithAuth

@WithAuth
@KsService
interface MyService : RpcService {
    @KsMethod("/op1")
    suspend fun op1(input: String): String

    @KsMethod("/op2")
    suspend fun op2(input: String): String
}
"""
        )
        val result = compile(listOf(source))
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "messages: ${result.messages}"
        )
        val bindings1 = findEndpointContextBindings(result, "MyService", "op1")
        assertEquals(1, bindings1.size)
        assertEquals("x-auth-token", bindings1.single().wireKey)

        val bindings2 = findEndpointContextBindings(result, "MyService", "op2")
        assertEquals(1, bindings2.size)
        assertEquals("x-auth-token", bindings2.single().wireKey)
    }

    @Test
    fun `class-level and method-level @KsContext annotations are unioned`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsContext
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.KsContextBinding
import com.monkopedia.ksrpc.RpcService
import kotlin.coroutines.CoroutineContext

class AuthToken(val token: String) : CoroutineContext.Element {
    override val key get() = Key
    companion object Key : KsContextBinding<AuthToken> {
        override val wireKey: String = "x-auth-token"
        override fun toWire(value: AuthToken): String = value.token
        override fun fromWire(encoded: String): AuthToken = AuthToken(encoded)
    }
}

class TraceId(val value: String) : CoroutineContext.Element {
    override val key get() = Key
    companion object Key : KsContextBinding<TraceId> {
        override val wireKey: String = "x-trace-id"
        override fun toWire(value: TraceId): String = value.value
        override fun fromWire(encoded: String): TraceId = TraceId(encoded)
    }
}

@KsContext(binding = AuthToken.Key::class)
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class WithAuth

@KsContext(binding = TraceId.Key::class)
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class WithTrace

@WithAuth
@KsService
interface MyService : RpcService {
    @KsMethod("/op")
    @WithTrace
    suspend fun op(input: String): String

    @KsMethod("/plain")
    suspend fun plain(input: String): String
}
"""
        )
        val result = compile(listOf(source))
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "messages: ${result.messages}"
        )
        // /op has class-level @WithAuth + method-level @WithTrace
        val opBindings = findEndpointContextBindings(result, "MyService", "op")
        assertEquals(2, opBindings.size, "Expected 2 bindings, got $opBindings")
        val wireKeys = opBindings.map { it.wireKey }.toSet()
        assertTrue("x-auth-token" in wireKeys, "Missing x-auth-token; got $wireKeys")
        assertTrue("x-trace-id" in wireKeys, "Missing x-trace-id; got $wireKeys")

        // /plain only has the class-level @WithAuth
        val plainBindings = findEndpointContextBindings(result, "MyService", "plain")
        assertEquals(1, plainBindings.size)
        assertEquals("x-auth-token", plainBindings.single().wireKey)
    }

    @Test
    fun `method without @KsContext has empty contextBindings`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

@KsService
interface NoContextService : RpcService {
    @KsMethod("/plain")
    suspend fun plain(input: String): String
}
"""
        )
        val result = compile(listOf(source))
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "messages: ${result.messages}"
        )
        val bindings = findEndpointContextBindings(result, "NoContextService", "plain")
        assertTrue(
            bindings.isEmpty(),
            "Expected empty contextBindings when no @KsContext present, got $bindings"
        )
    }

    // --- helpers -----------------------------------------------------------

    private data class BindingSnapshot(val wireKey: String)

    private fun findEndpointContextBindings(
        result: JvmCompilationResult,
        serviceFqName: String,
        endpointName: String
    ): List<BindingSnapshot> {
        val serviceClass = result.classLoader.loadClass(serviceFqName)
        val companion = serviceClass.getField("Companion").get(null)
        val findEndpoint = companion.javaClass.methods.single { it.name == "findEndpoint" }
        val rpcMethod = findEndpoint.invoke(companion, endpointName)
            ?: error("findEndpoint($endpointName) returned null on $serviceFqName")
        val getBindings = rpcMethod.javaClass.methods.singleOrNull {
            it.name == "getContextBindings"
        } ?: error(
            "RpcMethod for $serviceFqName.$endpointName has no getContextBindings() — " +
                "plugin did not emit the contextBindings constructor argument"
        )

        @Suppress("UNCHECKED_CAST")
        val rawList = getBindings.invoke(rpcMethod) as List<Any>
        return rawList.map { raw ->
            val cls = raw.javaClass
            val binding = cls.getMethod("getBinding").invoke(raw)!!
            val wireKey = binding.javaClass.getMethod("getWireKey").invoke(binding) as String
            BindingSnapshot(wireKey = wireKey)
        }
    }
}
