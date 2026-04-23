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
 * Two-stage `KotlinCompilation` tests covering cross-module usage of the ksrpc
 * compiler plugin (issue #54).
 *
 * Each test compiles a "library" module first that declares a `@KsService`
 * interface, then compiles a "consumer" module that references the library's
 * service — e.g. implementing it, constructing a `Stub`/`RpcObject`, or
 * calling `toStub<Foo>()`. The consumer's classpath includes the library's
 * `classesDir`, mirroring how a real downstream KMP module sees a dependency
 * module's already-compiled output.
 *
 * This exercises plugin code paths that behave differently for
 * already-compiled binary dependencies (FIR `getCallableNamesForClass` does
 * not fire for types loaded off the classpath, so the consumer's IR-phase
 * stub/obj generation has to resolve the library-emitted companion by
 * symbol lookup rather than by re-synthesizing it).
 */
class CrossModuleCompileTest {

    @Test
    fun `non-generic service in library, implementation in consumer`() {
        val library = SourceFile.kotlin(
            "Library.kt",
            """
package lib

import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

@KsService
interface Foo : RpcService {
    @KsMethod("/greet")
    suspend fun greet(name: String): String
}
"""
        )
        val libraryResult = CrossModuleTestHarness.compileLibrary(library)
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            libraryResult.exitCode,
            "library compile failed: ${libraryResult.messages}"
        )

        val consumer = SourceFile.kotlin(
            "App.kt",
            """
package app

import lib.Foo

class FooImpl : Foo {
    override suspend fun greet(name: String): String = "Hello, ${"$"}name"
}
"""
        )
        val consumerResult = CrossModuleTestHarness.compileConsumer(libraryResult, consumer)
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            consumerResult.exitCode,
            "consumer compile failed: ${consumerResult.messages}"
        )
    }

    @Test
    fun `non-generic service in library, stub construction in consumer`() {
        // Consumer constructs the library's plugin-generated Stub directly. This
        // verifies the companion/Stub classes emitted into the library's classfiles
        // are visible and callable from a downstream compilation — the core gap
        // flagged in issue #54.
        val library = SourceFile.kotlin(
            "Library.kt",
            """
package lib

import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

@KsService
interface Bar : RpcService {
    @KsMethod("/ping")
    suspend fun ping(u: Unit): String
}
"""
        )
        val libraryResult = CrossModuleTestHarness.compileLibrary(library)
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            libraryResult.exitCode,
            "library compile failed: ${libraryResult.messages}"
        )

        val consumer = SourceFile.kotlin(
            "App.kt",
            """
package app

import com.monkopedia.ksrpc.channels.SerializedService
import lib.Bar

fun <S> useStub(channel: SerializedService<S>): Bar = Bar.Stub(channel)
"""
        )
        val consumerResult = CrossModuleTestHarness.compileConsumer(libraryResult, consumer)
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            consumerResult.exitCode,
            "consumer compile failed: ${consumerResult.messages}"
        )
    }

    @Test
    fun `service in library, toStub consumer in app resolves library companion`() {
        // `toStub<T>()` is the reified-inline entry point in ksrpc-core — it calls
        // `rpcObject<T>()` whose expected body is `T.Companion`. The library's
        // plugin-emitted companion has to be resolvable from the consumer
        // compilation's reified call site or this fails to resolve.
        val library = SourceFile.kotlin(
            "Library.kt",
            """
package lib

import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

@KsService
interface Baz : RpcService {
    @KsMethod("/echo")
    suspend fun echo(value: String): String
}
"""
        )
        val libraryResult = CrossModuleTestHarness.compileLibrary(library)
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            libraryResult.exitCode,
            "library compile failed: ${libraryResult.messages}"
        )

        val consumer = SourceFile.kotlin(
            "App.kt",
            """
package app

import com.monkopedia.ksrpc.channels.SerializedService
import com.monkopedia.ksrpc.toStub
import lib.Baz

fun <S> useStub(channel: SerializedService<S>): Baz = channel.toStub<Baz, S>()
"""
        )
        val consumerResult = CrossModuleTestHarness.compileConsumer(libraryResult, consumer)
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            consumerResult.exitCode,
            "consumer compile failed: ${consumerResult.messages}"
        )
    }

    @Test
    fun `generic service in library, concrete-type usage in consumer`() {
        // Covers the generic-across-modules path: a 1-type-param `@KsService`
        // compiled in the library, then instantiated in the consumer with a
        // concrete `@Serializable` element type. The consumer must be able to
        // (a) subtype the library service at a concrete T and (b) construct
        // the library's generated `Stub<T>(channel, serializer)` form.
        val library = SourceFile.kotlin(
            "Library.kt",
            """
package lib

import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

@KsService
interface Stream<T> : RpcService {
    @KsMethod("/next")
    suspend fun next(u: Unit): T
}
"""
        )
        val libraryResult = CrossModuleTestHarness.compileLibrary(library)
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            libraryResult.exitCode,
            "library compile failed: ${libraryResult.messages}"
        )

        val consumer = SourceFile.kotlin(
            "App.kt",
            """
package app

import com.monkopedia.ksrpc.channels.SerializedService
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import lib.Stream

@Serializable
data class Item(val id: Int)

class ItemStream : Stream<Item> {
    override suspend fun next(u: Unit): Item = Item(0)
}

fun <S> useStub(
    channel: SerializedService<S>,
    serializer: KSerializer<Item>
): Stream<Item> = Stream.Stub<Item>(channel, serializer)
"""
        )
        val consumerResult = CrossModuleTestHarness.compileConsumer(libraryResult, consumer)
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            consumerResult.exitCode,
            "consumer compile failed: ${consumerResult.messages}"
        )
    }
}
