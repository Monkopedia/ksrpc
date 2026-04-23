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
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlinx.serialization.compiler.extensions.SerializationComponentRegistrar
import org.junit.Test

/**
 * Tests for #77 — compiler plugin captures `@KsError(code, type)` annotations
 * on `@KsMethod` functions and emits a `List<KsErrorMapping>` into the
 * generated `RpcMethod` descriptor.
 *
 * The assertion strategy here reflects the kotlin-compile-testing harness:
 * we compile a service and then reach into the resulting classloader to
 * instantiate the generated companion, invoke `findEndpoint`, and introspect
 * `rpcMethod.errorMappings` via reflection. This exercises the full plugin
 * pipeline (FIR + IR) end-to-end rather than unit-testing the IR builder in
 * isolation.
 */
class KsErrorBindingTest {

    @Test
    fun `single @KsError captures code, type, and serializer`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsError
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService
import kotlinx.serialization.Serializable

@Serializable
data class InitError(val retry: Boolean)

@KsService
interface MyService : RpcService {
    @KsMethod("/init")
    @KsError(code = 100, type = InitError::class)
    suspend fun init(input: String): Int
}
"""
        )
        val result = compileWithSerialization(listOf(source))
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "messages: ${result.messages}"
        )
        val mappings = findEndpointErrorMappings(result, "MyService", "init")
        assertEquals(1, mappings.size, "Expected exactly one mapping, got $mappings")
        val entry = mappings.single()
        assertEquals(100, entry.code)
        assertEquals("InitError", entry.dataType.simpleName)
        // The captured serializer must refer to the compiled InitError. Verify by
        // inspecting its descriptor's serialName — avoids depending on specific
        // internal `*Serializer` class naming conventions.
        val serialName = serialName(entry.dataSerializer)
        assertTrue(
            serialName.endsWith("InitError"),
            "Expected serializer for InitError (serialName ends with 'InitError'), " +
                "got '$serialName'"
        )
    }

    @Test
    fun `multiple @KsError annotations on one method are all captured`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsError
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService
import kotlinx.serialization.Serializable

@Serializable
data class InitError(val retry: Boolean)

@Serializable
data class VersionError(val expected: Int, val actual: Int)

@KsService
interface MultiService : RpcService {
    @KsMethod("/init")
    @KsError(code = 100, type = InitError::class)
    @KsError(code = 101, type = VersionError::class)
    suspend fun init(input: String): Int
}
"""
        )
        val result = compileWithSerialization(listOf(source))
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "messages: ${result.messages}"
        )
        val mappings = findEndpointErrorMappings(result, "MultiService", "init")
        assertEquals(
            2,
            mappings.size,
            "Expected two mappings, got ${mappings.map { it.code to it.dataType.simpleName }}"
        )
        val byCode = mappings.associateBy { it.code }
        assertEquals("InitError", byCode[100]?.dataType?.simpleName)
        assertEquals("VersionError", byCode[101]?.dataType?.simpleName)
    }

    @Test
    fun `method without @KsError has an empty errorMappings list`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

@KsService
interface NoErrorService : RpcService {
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
        val mappings = findEndpointErrorMappings(result, "NoErrorService", "plain")
        assertTrue(
            mappings.isEmpty(),
            "Expected empty errorMappings when no @KsError present, got $mappings"
        )
    }

    @Test
    fun `non-@Serializable error payload type is rejected at compile time`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsError
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

// Deliberately NOT @Serializable — the plugin must reject this with a clear diagnostic.
data class NotSerializable(val value: String)

@KsService
interface BadService : RpcService {
    @KsMethod("/op")
    @KsError(code = 42, type = NotSerializable::class)
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
            result.messages.contains("@KsError") &&
                result.messages.contains("NotSerializable") &&
                result.messages.contains("@Serializable"),
            "Expected @Serializable-validation diagnostic naming NotSerializable, " +
                "got: ${result.messages}"
        )
    }

    // --- helpers -----------------------------------------------------------

    /**
     * Chain the kotlinx-serialization and ksrpc compiler plugins through
     * kotlin-compile-testing. The serialization plugin synthesizes
     * `*.serializer()` / `$serializer` on `@Serializable` types so that the
     * ksrpc plugin's emitted `serializer<Type>()` call resolves at runtime —
     * without it the KSerializer lookup falls back to reflection and fails
     * with `SerializationException: Serializer for class 'X' is not found`.
     */
    private fun compileWithSerialization(sources: List<SourceFile>): JvmCompilationResult =
        KotlinCompilation().apply {
            this.sources = sources
            compilerPluginRegistrars = listOf<CompilerPluginRegistrar>(
                SerializationComponentRegistrar(),
                KsrpcComponentRegistrar()
            )
            inheritClassPath = true
        }.compile()

    /**
     * Simplified view of a captured `KsErrorMapping` for assertion purposes —
     * pulled out of the classloader-created instances via reflection so tests
     * don't need to reach into ksrpc-core's own classes.
     */
    private data class MappingSnapshot(
        val code: Int,
        val dataType: Class<*>,
        val dataSerializer: Any
    )

    private fun findEndpointErrorMappings(
        result: JvmCompilationResult,
        serviceFqName: String,
        endpointName: String
    ): List<MappingSnapshot> {
        val serviceClass = result.classLoader.loadClass(serviceFqName)
        val companion = serviceClass.getField("Companion").get(null)
        val findEndpoint = companion.javaClass.methods.single { it.name == "findEndpoint" }
        val rpcMethod = findEndpoint.invoke(companion, endpointName)
            ?: error("findEndpoint($endpointName) returned null on $serviceFqName")
        val getMappings = rpcMethod.javaClass.methods.singleOrNull {
            it.name == "getErrorMappings"
        } ?: error(
            "RpcMethod for $serviceFqName.$endpointName has no getErrorMappings() — " +
                "plugin did not emit the errorMappings constructor argument"
        )

        @Suppress("UNCHECKED_CAST")
        val rawList = getMappings.invoke(rpcMethod) as List<Any>
        return rawList.map { raw ->
            val cls = raw.javaClass
            MappingSnapshot(
                code = cls.getMethod("getCode").invoke(raw) as Int,
                dataType =
                    (cls.getMethod("getDataType").invoke(raw) as kotlin.reflect.KClass<*>).java,
                dataSerializer = cls.getMethod("getDataSerializer").invoke(raw)!!
            )
        }
    }

    /** Fetch `descriptor.serialName` off a `KSerializer<*>` via reflection. */
    private fun serialName(serializer: Any): String {
        val descriptor = serializer.javaClass.methods.single {
            it.name == "getDescriptor" && it.parameterCount == 0
        }.invoke(serializer)
        return descriptor.javaClass.methods.single {
            it.name == "getSerialName" && it.parameterCount == 0
        }.invoke(descriptor) as String
    }
}
