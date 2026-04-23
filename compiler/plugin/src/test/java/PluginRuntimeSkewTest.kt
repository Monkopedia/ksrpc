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
import java.io.File
import java.util.zip.ZipFile
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Test

/**
 * Regression tests for the compiler plugin's soft-fallback paths that keep a new
 * plugin working against an older `ksrpc-core` runtime on the compile classpath
 * (issue #55).
 *
 * The plugin guards on three independently-resolved symbol clusters:
 *   - [KsrpcGenerationEnvironment.metadataSupported] — `MethodMetadata` and its
 *     `MetadataValue.*` subtypes (added in #11). When `false`, the plugin emits
 *     the legacy four-arg `RpcMethod` constructor call.
 *   - `RpcObjectFactory` (added in #41). When missing the FIR companion for a
 *     generic `@KsService` omits the factory supertype and its
 *     `create`/`arity` members.
 *   - `Flow` / binary-adapter transformer modules — the plugin does not emit
 *     auto-wiring for types it cannot resolve, and services that don't use those
 *     types compile cleanly without the adapter modules on the classpath.
 *
 * The plugin-tests classpath is layered: locally-built `ksrpc-core-jvm-0.11.1.jar`
 * (from `../ksrpc-core/build/libs/`) wins ahead of the published `ksrpctest`
 * coordinate (Maven Central 0.11.1). The published 0.11.1 coordinate predates the
 * metadata / factory work, so stripping the locally-built jar from the classpath
 * effectively downgrades the test runtime to the published baseline. This file
 * uses that layering to drive the plugin down the soft-fallback branches.
 */
class PluginRuntimeSkewTest {

    /**
     * Path substring identifying the locally-built `ksrpc-core` jvm jar that is
     * stitched onto the test classpath by `build.gradle.kts`. Excluding this
     * falls back to the published `ksrpctest` 0.11.1 coordinate, which predates
     * `MethodMetadata` and `RpcObjectFactory`.
     */
    private val localCoreJarMarker = "ksrpc-core/build/libs"

    /**
     * `@KsMethodMetadata` lives in `ksrpc-api`, whose published artifact (at the
     * `libs.ksrpctest` coordinate) does not yet ship the annotation. The plugin
     * only cares about the FQN of the annotation class, so we inline it into
     * the test sources with the correct package — this matches the pattern in
     * [KsNotificationValidationTest].
     */
    private val metadataAnnotations = SourceFile.kotlin(
        "Annotations.kt",
        """
package com.monkopedia.ksrpc.annotation

@Target(AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class KsMethodMetadata

@KsMethodMetadata
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class DemoMarker(val tag: String)
"""
    )

    // --- metadata soft-fallback --------------------------------------------

    @Test
    fun `metadata annotation compiles when new runtime present`() {
        // Baseline: compile against the default classpath (local ksrpc-core jar
        // wins → MethodMetadata available). Expect compile OK and verify that
        // MethodMetadata is reachable from the compiled code's classloader.
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.annotation.DemoMarker
import com.monkopedia.ksrpc.RpcService

@KsService
interface Svc : RpcService {
    @KsMethod("/go")
    @DemoMarker(tag = "hello")
    suspend fun go(u: Unit): Int
}
"""
        )
        val result = compile(sourceFiles = listOf(metadataAnnotations, source))
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "messages: ${result.messages}"
        )
        val hasMetadata = runtimeHasClass(
            result.classLoader,
            "com.monkopedia.ksrpc.MethodMetadata"
        )
        assertTrue(
            hasMetadata,
            "Expected MethodMetadata on the effective runtime classpath when the " +
                "local ksrpc-core jar is in play"
        )
    }

    // NOTE (#111): the three tests below currently assert that compilation
    // *fails* with a `ServiceExecutor` class-not-found message. This documents
    // a genuine breaking change: `ServiceExecutor` was moved from
    // `com.monkopedia.ksrpc` to `com.monkopedia.ksrpc.internal`, and the
    // published `libs.ksrpctest` baseline (0.11.1) still only exposes the
    // old FQN. `ServiceExecutor` has always been a hard dependency of the
    // plugin, so this is not a soft-fallback case — the plugin correctly
    // fails fast. Once `libs.ksrpctest` is bumped past the rename, these
    // tests should be restored to their original `ExitCode.OK` contract so
    // they once again exercise the `MethodMetadata` / `RpcObjectFactory`
    // soft-fallback branches they were designed for.

    private val serviceExecutorMissingMarker =
        "can't resolve com/monkopedia/ksrpc/internal/ServiceExecutor"

    @Test
    fun `plugin hard-fails on ServiceExecutor FQN rename when metadata missing`() {
        // Strip the locally-built ksrpc-core jar — the published 0.11.1
        // coordinate becomes the effective runtime. The plugin would reach
        // the `metadataSupported=false` soft-fallback branch, but first it
        // tries to resolve `ServiceExecutor` at its new internal-package FQN,
        // which the 0.11.1 baseline does not ship. Pin that failure mode so
        // a silent regression to either a NoClassDefFoundError or to an
        // OK-compile with broken generated bytecode is caught loudly.
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.annotation.DemoMarker
import com.monkopedia.ksrpc.RpcService

@KsService
interface Svc : RpcService {
    @KsMethod("/go")
    @DemoMarker(tag = "hello")
    suspend fun go(u: Unit): Int
}
"""
        )
        val result = compileWithoutLocalCore(listOf(metadataAnnotations, source))
        assertFalse(
            effectiveClasspathContainsClass(
                "com/monkopedia/ksrpc/MethodMetadata.class"
            ),
            "Precondition failed: effective classpath still contains MethodMetadata"
        )
        assertEquals(
            KotlinCompilation.ExitCode.INTERNAL_ERROR,
            result.exitCode,
            "Expected hard-fail on ServiceExecutor FQN rename (#111). " +
                "messages: ${result.messages}"
        )
        assertTrue(
            result.messages.contains(serviceExecutorMissingMarker),
            "Expected diagnostic to mention missing ServiceExecutor at new FQN. " +
                "messages: ${result.messages}"
        )
    }

    // --- factory (RpcObjectFactory) soft-fallback --------------------------

    @Test
    fun `non-generic service hard-fails on ServiceExecutor FQN rename`() {
        // Non-generic @KsService does not need RpcObjectFactory; the
        // FirCompanionDeclarationGenerator only emits the factory supertype
        // for generic services. This test was originally a soft-fallback
        // happy-path, but currently fails first on the ServiceExecutor
        // rename (#111). Pin the hard-fail so any shift — back to OK, or to
        // a different error — is caught.
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

@KsService
interface Svc : RpcService {
    @KsMethod("/ping")
    suspend fun ping(u: Unit): String
}
"""
        )
        val result = compileWithoutLocalCore(source)
        assertFalse(
            effectiveClasspathContainsClass(
                "com/monkopedia/ksrpc/RpcObjectFactory.class"
            ),
            "Precondition failed: effective classpath still contains RpcObjectFactory"
        )
        assertEquals(
            KotlinCompilation.ExitCode.INTERNAL_ERROR,
            result.exitCode,
            "Expected hard-fail on ServiceExecutor FQN rename (#111). " +
                "messages: ${result.messages}"
        )
        assertTrue(
            result.messages.contains(serviceExecutorMissingMarker),
            "Expected diagnostic to mention missing ServiceExecutor at new FQN. " +
                "messages: ${result.messages}"
        )
    }

    @Test
    fun `generic service hard-fails on ServiceExecutor FQN rename`() {
        // Originally pinned the RpcObjectFactory `factorySupported=false`
        // soft-fallback branch for generic services. Currently it fails
        // first on the ServiceExecutor rename (#111). Pin the hard-fail
        // so the restoration point for #111 is obvious.
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

@KsService
interface KsStream<T> : RpcService {
    @KsMethod("/next")
    suspend fun next(u: Unit): T
}
"""
        )
        val result = compileWithoutLocalCore(source)
        assertFalse(
            effectiveClasspathContainsClass(
                "com/monkopedia/ksrpc/RpcObjectFactory.class"
            ),
            "Precondition failed: effective classpath still contains RpcObjectFactory"
        )
        assertEquals(
            KotlinCompilation.ExitCode.INTERNAL_ERROR,
            result.exitCode,
            "Expected hard-fail on ServiceExecutor FQN rename (#111). " +
                "messages: ${result.messages}"
        )
        assertTrue(
            result.messages.contains(serviceExecutorMissingMarker),
            "Expected diagnostic to mention missing ServiceExecutor at new FQN. " +
                "messages: ${result.messages}"
        )
    }

    // --- optional adapter modules: not required when unused ----------------

    @Test
    fun `service without Flow or binary types compiles without adapter modules`() {
        // Sanity check: a service that uses none of the optional types should
        // compile even when every adapter jar is stripped. This pins that the
        // plugin's adapter lookups are demand-driven — it never dereferences a
        // missing adapter just because the adapter module is absent.
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

@KsService
interface Svc : RpcService {
    @KsMethod("/echo")
    suspend fun echo(input: String): String
}
"""
        )
        val result = compileStrippingOptionalAdapters(source)
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "Service with no Flow/binary types must compile when adapter " +
                "modules are not on the classpath. messages: ${result.messages}"
        )
    }

    // --- helpers -----------------------------------------------------------

    /**
     * Compile with the locally-built ksrpc-core jvm jar stripped — the published
     * `ksrpctest` 0.11.1 coordinate becomes the effective ksrpc-core runtime.
     * The published 0.11.1 jar lacks `MethodMetadata` and `RpcObjectFactory`,
     * exercising the plugin's soft-fallback branches.
     */
    private fun compileWithoutLocalCore(sources: List<SourceFile>): JvmCompilationResult =
        compileFiltered(sources) { entry ->
            !entry.absolutePath.replace(File.separatorChar, '/')
                .contains(localCoreJarMarker)
        }

    private fun compileWithoutLocalCore(source: SourceFile): JvmCompilationResult =
        compileWithoutLocalCore(listOf(source))

    /**
     * Compile with every `ksrpc-binary-*` and `ksrpc-flow-*` adapter jar
     * stripped. Used by the happy-path sanity test where the service under
     * compilation does not reference any optional type.
     */
    private fun compileStrippingOptionalAdapters(source: SourceFile): JvmCompilationResult =
        compileFiltered(listOf(source)) { entry ->
            val p = entry.absolutePath.replace(File.separatorChar, '/')
            !p.contains("ksrpc-binary-") && !p.contains("ksrpc-flow")
        }

    private fun compileFiltered(
        sources: List<SourceFile>,
        keep: (File) -> Boolean
    ): JvmCompilationResult {
        val filtered = System.getProperty("java.class.path")
            .split(File.pathSeparatorChar)
            .map(::File)
            .filter(keep)
        return KotlinCompilation().apply {
            this.sources = sources
            compilerPluginRegistrars = listOf(KsrpcComponentRegistrar())
            inheritClassPath = false
            classpaths = filtered
        }.compile()
    }

    private fun runtimeHasClass(loader: ClassLoader, fqn: String): Boolean = try {
        Class.forName(fqn, false, loader)
        true
    } catch (_: ClassNotFoundException) {
        false
    } catch (_: NoClassDefFoundError) {
        false
    }

    /**
     * True iff any jar on the effective (local-core-stripped) classpath
     * contains the given classfile entry (e.g. `com/monkopedia/ksrpc/Foo.class`).
     *
     * We introspect the jars directly rather than going through a classloader
     * because the classloader could find the class via an unintended jar.
     * Jar-level introspection matches what the Kotlin compiler sees for the
     * filtered classpath used by [compileWithoutLocalCore].
     */
    private fun effectiveClasspathContainsClass(classEntry: String): Boolean {
        val cp = System.getProperty("java.class.path").split(File.pathSeparatorChar)
            .map(::File)
            .filter { it.isFile && it.name.endsWith(".jar") }
            .filter {
                !it.absolutePath.replace(File.separatorChar, '/')
                    .contains(localCoreJarMarker)
            }
        return cp.any { jar ->
            runCatching {
                ZipFile(jar).use { zf -> zf.getEntry(classEntry) != null }
            }.getOrDefault(false)
        }
    }
}
