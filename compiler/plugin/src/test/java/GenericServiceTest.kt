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
    fun `generic ksservice with KsNotification on Unit method compiles`() {
        // Sanity check that metadata sibling annotations combine with generic services.
        // Regressions here would surface as either a companion/stub generation failure or a
        // surprise "must return Unit" misfire on a generic method whose return type is T.
        val annotations = SourceFile.kotlin(
            "Annotations.kt",
            """
package com.monkopedia.ksrpc.annotation

@Target(AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class KsMethodMetadata

@KsMethodMetadata
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class KsNotification
"""
        )
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsNotification
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

@KsService
interface GenericWithNotification<T> : RpcService {
    @KsMethod("/publish")
    @KsNotification
    suspend fun publish(item: T)
}
"""
        )
        val result = compile(listOf(annotations, source))
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "messages: ${result.messages}"
        )
    }

    @Test
    fun `generic ksservice with multiple type parameters compiles`() {
        // Services with more than one invariant type parameter must generate a Stub/Obj
        // whose primary constructors take one KSerializer per type parameter in declaration
        // order. Catching a regression here means we verified argument-ordering between FIR
        // stub synthesis and IR body generation doesn't drift.
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService
import com.monkopedia.ksrpc.channels.SerializedService
import kotlinx.serialization.KSerializer

@KsService
interface KsPairStream<A, B> : RpcService {
    @KsMethod("/next")
    suspend fun next(u: Unit): Map<A, B>
}

fun <S> useStub(
    channel: SerializedService<S>,
    a: KSerializer<String>,
    b: KSerializer<Int>
): KsPairStream<String, Int> = KsPairStream.Stub<String, Int>(channel, a, b)
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
    fun `generic ksservice with generic subservice parameter compiles`() {
        // Exercises the ObjGeneration fix that lets a generic `@KsService` accept a
        // generic sub-service in its method signature. Before the fix, the compiler
        // plugin rejected `foo(sub: Sub<T>)` with "Cannot compose a KSerializer for
        // Sub<T>" because the generic-path createTypeConverter always fell through to
        // the KSerializer composer instead of recognizing the RpcService supertype.
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

@KsService
interface Sub<T> : RpcService {
    @KsMethod("/x")
    suspend fun x(t: T)
}

@KsService
interface Outer<T> : RpcService {
    @KsMethod("/take")
    suspend fun take(sub: Sub<T>)

    @KsMethod("/give")
    suspend fun give(u: Unit): Sub<T>
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
    fun `generic ksservice with non-generic subservice parameter compiles`() {
        // The generic-service createTypeConverter must also route non-generic sub-services
        // through SubserviceTransformer rather than the KSerializer composer.
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

@KsService
interface Token : RpcService {
    @KsMethod("/hit")
    suspend fun hit(u: Unit)
}

@KsService
interface GenericHolder<T> : RpcService {
    @KsMethod("/token")
    suspend fun token(u: Unit): Token
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
    fun `generic ksservice with many methods does not trigger ConcurrentModificationException`() {
        // Regression test for #58 / original crash from #41. Before the fix in PR #42, a
        // generic `@KsService` with multiple methods would throw a
        // ConcurrentModificationException during IR generation because body-time codegen for
        // each method (via ObjGeneration / StubGeneration) created a fresh nested executor
        // class as a side effect while the visitor was still iterating the parent class's
        // declarations. The fix pre-creates those executors in
        // `generateChildrenForClass` and stashes them on `ServiceClass.genericExecutors`, so
        // body generation only has to look them up.
        //
        // A refactor that moves executor creation back inline would re-introduce the CME on
        // the exact shape below. The assertion is that compilation SUCCEEDS and the compile
        // messages do not mention ConcurrentModificationException (the compile-testing
        // framework surfaces IR-time exceptions through `result.messages`).
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

@KsService
interface ManyMethods<T> : RpcService {
    @KsMethod("/a")
    suspend fun a(x: T): T

    @KsMethod("/b")
    suspend fun b(x: List<T>): List<T>

    @KsMethod("/c")
    suspend fun c(x: Map<String, T>): T

    @KsMethod("/d")
    suspend fun d(x: Set<T>): Set<T>

    @KsMethod("/e")
    suspend fun e(x: T?): T?
}
"""
        )
        val result = compile(sourceFile = source)
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "messages: ${result.messages}"
        )
        assertTrue(
            !result.messages.contains("ConcurrentModificationException"),
            "Compile output must not mention ConcurrentModificationException (regression " +
                "for #58 / original crash from #41). messages: ${result.messages}"
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
    @Test
    fun `generic ksservice with KsTimeout on T-returning method compiles`() {
        // Sibling metadata annotation `@KsTimeout` must compose with the generic-service
        // stub/obj generation path. A regression here would fail as either a
        // companion/stub generation error or a "missing metadata" runtime lookup.
        val annotations = SourceFile.kotlin(
            "Annotations.kt",
            """
package com.monkopedia.ksrpc.annotation

@Target(AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class KsMethodMetadata

@KsMethodMetadata
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class KsTimeout(
    val millis: Long = 0L,
    val seconds: Long = 0L,
    val minutes: Long = 0L
)
"""
        )
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.annotation.KsTimeout
import com.monkopedia.ksrpc.RpcService

@KsService
interface GenericWithTimeout<T> : RpcService {
    @KsMethod("/get")
    @KsTimeout(millis = 500)
    suspend fun get(x: T): T
}
"""
        )
        val result = compile(listOf(annotations, source))
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "messages: ${result.messages}"
        )
    }

    @Test
    fun `generic ksservice with user metadata annotation on T method compiles`() {
        // User sibling metadata annotations (marked with `@KsMethodMetadata`) on a generic
        // service must be captured via the same metadata pipeline as built-in ones.
        // This covers shape (5) in issue #57.
        val annotations = SourceFile.kotlin(
            "Annotations.kt",
            """
package com.monkopedia.ksrpc.annotation

@Target(AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class KsMethodMetadata
"""
        )
        val userAnnotation = SourceFile.kotlin(
            "Audit.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethodMetadata

@KsMethodMetadata
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class Audit(val level: String)
"""
        )
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

@KsService
interface GenericWithAudit<T> : RpcService {
    @KsMethod("/track")
    @Audit("high")
    suspend fun track(x: T)
}
"""
        )
        val result = compile(listOf(annotations, userAnnotation, source))
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "messages: ${result.messages}"
        )
    }

    @Test
    fun `generic ksservice reached through deep super chain compiles`() {
        // Issue #102 — `@KsService` on `D<T>` where `RpcService` is reached
        // transitively through two plain-Kotlin intermediate interfaces
        // (`D -> C -> B -> A -> RpcService`). The plugin now walks transitive
        // supertypes so this shape compiles successfully.
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

interface A : RpcService
interface B : A
interface C : B

@KsService
interface D<T> : C {
    @KsMethod("/echo")
    suspend fun echo(x: T): T
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
    fun `generic ksservice with shallow plain-Kotlin super chain compiles`() {
        // Companion test for the deep-chain case: one plain-Kotlin intermediate is the
        // supported shape today (`A : RpcService` is the direct supertype). Verifies the
        // existing validation still accepts this shape on a generic service.
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

@KsService
interface GenericDirectChild<T> : RpcService {
    @KsMethod("/echo")
    suspend fun echo(x: T): T
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
    fun `non-generic subtype of generic ksservice compiles`() {
        // Issue #95 — Scenario 1: plain Kotlin interface that specializes a generic
        // @KsService with concrete type args.
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

@KsService
interface GenericSvc<T> : RpcService {
    @KsMethod("/echo")
    suspend fun echo(item: T): T
}

interface TypedSvc : GenericSvc<String>
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
    fun `generic subtype of generic ksservice compiles`() {
        // Issue #95 — Scenario 2: plain Kotlin interface that forwards type params
        // to a generic @KsService.
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

@KsService
interface GenericSvc<T> : RpcService {
    @KsMethod("/echo")
    suspend fun echo(item: T): T
}

interface TypedSvcT<T> : GenericSvc<T>
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
    fun `non-generic subtype of non-generic ksservice compiles`() {
        // Issue #95 — plain subtype of a non-generic @KsService.
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

@KsService
interface SimpleSvc : RpcService {
    @KsMethod("/ping")
    suspend fun ping(msg: String): String
}

interface SubSimpleSvc : SimpleSvc
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
    fun `generic ksservice with flipped wrapper type parameters compiles`() {
        // Shape (6) in issue #57 — `Map<K, V>` in, `Map<V, K>` out. Verifies the generic
        // wrapper-serializer composer threads K and V into independent slots in both the
        // input and output transforms.
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

@KsService
interface Pair2<K, V> : RpcService {
    @KsMethod("/swap")
    suspend fun swap(x: Map<K, V>): Map<V, K>
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


}
