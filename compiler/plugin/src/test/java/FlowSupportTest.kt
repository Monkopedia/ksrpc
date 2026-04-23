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
 * Covers the compiler plugin's auto-detection of `Flow<T>` and `KsFlowService<T>`
 * in `@KsMethod` signatures (issue #39).
 *
 * Relies on the locally-built `ksrpc-flow` jvmJar being on the test classpath
 * (wired via the `files(...)` test dependency in `build.gradle.kts`) so the
 * user code under test can resolve `kotlinx.coroutines.flow.Flow`,
 * `com.monkopedia.ksrpc.flow.KsFlowService<T>`, and
 * `com.monkopedia.ksrpc.flow.FlowSubserviceTransformer<T>`.
 */
class FlowSupportTest {

    @Test
    fun `ksservice with Flow return compiles`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
data class Update(val id: String)

@KsService
interface MyService : RpcService {
    @KsMethod("/updates")
    suspend fun updates(filter: String): Flow<Update>
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
    fun `ksservice with Flow parameter compiles`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
data class Chunk(val bytes: String)

@Serializable
data class Receipt(val ok: Boolean)

@KsService
interface UploadService : RpcService {
    @KsMethod("/upload")
    suspend fun upload(items: Flow<Chunk>): Receipt
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
    fun `ksservice with Flow in both positions compiles`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
data class Item(val v: Int)

@KsService
interface Pipe : RpcService {
    @KsMethod("/pipe")
    suspend fun pipe(items: Flow<Item>): Flow<Item>
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
    fun `ksservice mixing Flow return, normal method, and raw KsFlowService compiles`() {
        // Covers the "doesn't regress raw KsFlowService<T>" requirement — a service
        // that uses `Flow<T>` in some methods and raw `KsFlowService<T>` in others
        // must still compile, with each method getting the right transform.
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService
import com.monkopedia.ksrpc.flow.KsFlowService
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
data class Event(val kind: String)

@Serializable
data class Ack(val seq: Int)

@KsService
interface Mixed : RpcService {
    @KsMethod("/events")
    suspend fun events(filter: String): Flow<Event>

    @KsMethod("/raw")
    suspend fun raw(u: Unit): KsFlowService<Event>

    @KsMethod("/ack")
    suspend fun ack(u: Unit): Ack
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
    fun `generic outer service with Flow return compiles`() {
        // The element type inside Flow<T> here is the outer service's class-level
        // type parameter — exercises the generic-path codegen that composes the
        // element serializer from the injected KSerializer<T> field rather than
        // from a static serializer<T>() call.
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService
import kotlinx.coroutines.flow.Flow

@KsService
interface Stream<T> : RpcService {
    @KsMethod("/items")
    suspend fun items(u: Unit): Flow<T>
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
    fun `generic outer service with Flow parameter compiles`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService
import kotlinx.coroutines.flow.Flow

@KsService
interface Sink<T> : RpcService {
    @KsMethod("/push")
    suspend fun push(items: Flow<T>)
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
