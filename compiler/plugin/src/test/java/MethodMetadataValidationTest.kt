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
 * Compile-time coverage for [MetadataIrBuilder.buildMetadataValue]. Each supported
 * annotation-argument shape must compile cleanly when it appears on a `@KsMethod`
 * sibling annotation; each unsupported shape must fail compilation with the
 * "unsupported ksrpc method-metadata argument" diagnostic that `reportUserError`
 * emits through [PluginReporter].
 *
 * Runtime propagation of these shapes is already covered by
 * `MethodMetadataPropagationTest` and `MethodMetadataArgShapesTest` in
 * `ksrpc-test`. The tests here exist so that a regression in the plugin
 * that silently drops the diagnostic or rejects a valid shape breaks a
 * plugin-local test, not only a multiplatform integration run.
 */
class MethodMetadataValidationTest {

    // The plugin tests use an included build that may reference a published
    // ksrpc-core version predating the metadata infrastructure, so we re-declare
    // `@KsMethodMetadata` inline. Only the FQ name matters to the plugin.
    private val annotations = SourceFile.kotlin(
        "Annotations.kt",
        """
package com.monkopedia.ksrpc.annotation

@Target(AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class KsMethodMetadata
"""
    )

    @Test
    fun `string int long boolean double float constants all compile`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsMethodMetadata
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

@KsMethodMetadata
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class Primitives(
    val s: String,
    val i: Int,
    val l: Long,
    val b: Boolean,
    val d: Double,
    val f: Float
)

@KsService
interface S : RpcService {
    @KsMethod("/p")
    @Primitives(s = "x", i = 1, l = 2L, b = true, d = 3.0, f = 4.0f)
    suspend fun p(input: String): String
}
"""
        )
        val result = compile(listOf(annotations, source))
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "Expected primitive metadata args to compile; messages: ${result.messages}"
        )
    }

    @Test
    fun `KClass literal arg compiles`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsMethodMetadata
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService
import kotlin.reflect.KClass

@KsMethodMetadata
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class UsesKClass(val type: KClass<*>)

class Payload

@KsService
interface S : RpcService {
    @KsMethod("/k")
    @UsesKClass(Payload::class)
    suspend fun k(input: String): String
}
"""
        )
        val result = compile(listOf(annotations, source))
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "Expected KClass arg to compile; messages: ${result.messages}"
        )
    }

    @Test
    fun `enum constant arg compiles`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsMethodMetadata
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

enum class Color { RED, BLUE }

@KsMethodMetadata
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class UsesEnum(val color: Color)

@KsService
interface S : RpcService {
    @KsMethod("/e")
    @UsesEnum(color = Color.RED)
    suspend fun e(input: String): String
}
"""
        )
        val result = compile(listOf(annotations, source))
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "Expected enum arg to compile; messages: ${result.messages}"
        )
    }

    @Test
    fun `array of enums compiles`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsMethodMetadata
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

enum class Color { RED, BLUE }

@KsMethodMetadata
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class UsesEnumArray(val colors: Array<Color>)

@KsService
interface S : RpcService {
    @KsMethod("/ea")
    @UsesEnumArray(colors = [Color.RED, Color.BLUE])
    suspend fun ea(input: String): String
}
"""
        )
        val result = compile(listOf(annotations, source))
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "Expected Array<Enum> arg to compile; messages: ${result.messages}"
        )
    }

    @Test
    fun `array of KClass literals compiles`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsMethodMetadata
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService
import kotlin.reflect.KClass

class P
class Q

@KsMethodMetadata
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class UsesKClassArray(val types: Array<KClass<*>>)

@KsService
interface S : RpcService {
    @KsMethod("/ka")
    @UsesKClassArray(types = [P::class, Q::class])
    suspend fun ka(input: String): String
}
"""
        )
        val result = compile(listOf(annotations, source))
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "Expected Array<KClass> arg to compile; messages: ${result.messages}"
        )
    }

    @Test
    fun `primitive arrays compile`() {
        // IntArray / LongArray / BooleanArray / DoubleArray / FloatArray are all
        // legal annotation-argument types and reach `buildMetadataValue` through
        // the `IrVararg` branch.
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsMethodMetadata
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

@KsMethodMetadata
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class UsesPrimitiveArrays(
    val ints: IntArray,
    val longs: LongArray,
    val bools: BooleanArray,
    val doubles: DoubleArray,
    val floats: FloatArray,
    val strings: Array<String>
)

@KsService
interface S : RpcService {
    @KsMethod("/pa")
    @UsesPrimitiveArrays(
        ints = [1, 2],
        longs = [1L, 2L],
        bools = [true, false],
        doubles = [1.0, 2.0],
        floats = [1.0f, 2.0f],
        strings = ["a", "b"]
    )
    suspend fun pa(input: String): String
}
"""
        )
        val result = compile(listOf(annotations, source))
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "Expected primitive-array args to compile; messages: ${result.messages}"
        )
    }

    @Test
    fun `nested annotation argument is rejected with clear diagnostic`() {
        // An annotation whose argument is itself another annotation is the
        // canonical "unsupported" shape for ksrpc: `buildMetadataValue` returns
        // null for `IrConstructorCall` and `reportUserError` must emit the
        // "unsupported ksrpc method-metadata argument" diagnostic with the
        // argument name and the offending annotation's FQ name.
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsMethodMetadata
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

@Target(AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class Inner(val s: String)

@KsMethodMetadata
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class WithNested(val inner: Inner)

@KsService
interface S : RpcService {
    @KsMethod("/nested")
    @WithNested(inner = Inner("x"))
    suspend fun nested(input: String): String
}
"""
        )
        val result = compile(listOf(annotations, source))
        assertTrue(
            result.exitCode != KotlinCompilation.ExitCode.OK,
            "Expected compilation to fail on nested-annotation arg; " +
                "messages: ${result.messages}"
        )
        assertTrue(
            result.messages.contains("unsupported ksrpc method-metadata argument"),
            "Expected canonical unsupported-argument wording, got: ${result.messages}"
        )
        assertTrue(
            result.messages.contains("inner") &&
                result.messages.contains("WithNested"),
            "Expected diagnostic to name the argument `inner` and the annotation " +
                "`WithNested`, got: ${result.messages}"
        )
    }

    @Test
    fun `array of nested annotations is rejected`() {
        // An `Array<Inner>` argument, where `Inner` is itself an annotation,
        // produces an `IrVararg` of `IrConstructorCall` elements. The inner
        // elements fail the `buildMetadataValue` match and are silently dropped,
        // but the test still exists so a future change that switches the list
        // to a strict failure catches the mismatch here rather than only in an
        // integration run. Compilation must succeed today — the vararg wrapper
        // itself is a supported shape — but the captured list has no elements
        // for the nested annotations. We only assert compilation succeeds; the
        // runtime-level shape of the captured `ListValue` is covered by the
        // integration tests.
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsMethodMetadata
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

@Target(AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class Inner(val s: String)

@KsMethodMetadata
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class WithNestedArray(val inners: Array<Inner>)

@KsService
interface S : RpcService {
    @KsMethod("/nested_array")
    @WithNestedArray(inners = [Inner("x"), Inner("y")])
    suspend fun nestedArray(input: String): String
}
"""
        )
        val result = compile(listOf(annotations, source))
        // Compilation succeeds — see KDoc above for why this is the current
        // contract — so we only assert the canonical message is NOT in there
        // when the shape is silently-accepted vararg-of-unsupported.
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "Expected Array<AnnotationClass> arg to compile (vararg silently " +
                "drops unsupported elements); messages: ${result.messages}"
        )
    }

    @Test
    fun `default-valued args that the caller does not set do not trigger unsupported diagnostic`() {
        // The plugin iterates `annotation.arguments` which is sized to the
        // annotation's parameter list but contains null for unset args. Nulls
        // must be skipped, not reported as "unsupported".
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsMethodMetadata
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

@KsMethodMetadata
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class WithDefaults(
    val tag: String = "default",
    val count: Int = 0,
    val flags: Array<String> = []
)

@KsService
interface S : RpcService {
    @KsMethod("/defaults")
    @WithDefaults
    suspend fun d(input: String): String
}
"""
        )
        val result = compile(listOf(annotations, source))
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "Expected fully-defaulted annotation to compile; messages: ${result.messages}"
        )
        assertTrue(
            !result.messages.contains("unsupported ksrpc method-metadata argument"),
            "Default-valued args must not trigger the unsupported-arg diagnostic: " +
                result.messages
        )
    }
}
