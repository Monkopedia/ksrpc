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
 * Compiler plugin tests for service capability tier validation (issue #146).
 *
 * Verifies that the FIR-phase `KsServiceClassChecker` enforces the type hierarchy:
 *   - `RpcService` — simple input/output only
 *   - `RpcHostService` — may return sub-services
 *   - `RpcBidiService` — may accept sub-service inputs, use `Flow<T>`
 */
class ServiceTierValidationTest {

    @Test
    fun `RpcService with sub-service output is rejected, suggests RpcHostService`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

@KsService
interface SubService : RpcService {
    @KsMethod("/echo")
    suspend fun echo(input: String): String
}

@KsService
interface ParentService : RpcService {
    @KsMethod("/child")
    suspend fun child(input: String): SubService
}
"""
        )
        val result = compile(sourceFile = source)
        assertEquals(
            KotlinCompilation.ExitCode.COMPILATION_ERROR,
            result.exitCode,
            "messages: ${result.messages}"
        )
        assertTrue(
            result.messages.contains("ParentService") &&
                result.messages.contains("child") &&
                result.messages.contains("RpcHostService"),
            "Expected diagnostic mentioning ParentService, method 'child', and RpcHostService; " +
                "got: ${result.messages}"
        )
    }

    @Test
    fun `RpcService with sub-service input is rejected, suggests RpcBidiService`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

@KsService
interface Callback : RpcService {
    @KsMethod("/notify")
    suspend fun notify(msg: String)
}

@KsService
interface Producer : RpcService {
    @KsMethod("/subscribe")
    suspend fun subscribe(cb: Callback): String
}
"""
        )
        val result = compile(sourceFile = source)
        assertEquals(
            KotlinCompilation.ExitCode.COMPILATION_ERROR,
            result.exitCode,
            "messages: ${result.messages}"
        )
        assertTrue(
            result.messages.contains("Producer") &&
                result.messages.contains("subscribe") &&
                result.messages.contains("RpcBidiService"),
            "Expected diagnostic mentioning Producer, method 'subscribe', and RpcBidiService; " +
                "got: ${result.messages}"
        )
    }

    @Test
    fun `RpcService returning Flow is rejected, suggests RpcBidiService`() {
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
interface StreamService : RpcService {
    @KsMethod("/updates")
    suspend fun updates(filter: String): Flow<Update>
}
"""
        )
        val result = compile(sourceFile = source)
        assertEquals(
            KotlinCompilation.ExitCode.COMPILATION_ERROR,
            result.exitCode,
            "messages: ${result.messages}"
        )
        assertTrue(
            result.messages.contains("StreamService") &&
                result.messages.contains("updates") &&
                result.messages.contains("RpcBidiService"),
            "Expected diagnostic mentioning StreamService, method 'updates', and RpcBidiService; " +
                "got: ${result.messages}"
        )
    }

    @Test
    fun `RpcHostService with sub-service output compiles OK`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcHostService
import com.monkopedia.ksrpc.RpcService

@KsService
interface SubService : RpcService {
    @KsMethod("/echo")
    suspend fun echo(input: String): String
}

@KsService
interface ParentService : RpcHostService {
    @KsMethod("/child")
    suspend fun child(input: String): SubService
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
    fun `RpcHostService with sub-service input is rejected, suggests RpcBidiService`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcHostService
import com.monkopedia.ksrpc.RpcService

@KsService
interface Callback : RpcService {
    @KsMethod("/notify")
    suspend fun notify(msg: String)
}

@KsService
interface Producer : RpcHostService {
    @KsMethod("/subscribe")
    suspend fun subscribe(cb: Callback): String
}
"""
        )
        val result = compile(sourceFile = source)
        assertEquals(
            KotlinCompilation.ExitCode.COMPILATION_ERROR,
            result.exitCode,
            "messages: ${result.messages}"
        )
        assertTrue(
            result.messages.contains("Producer") &&
                result.messages.contains("subscribe") &&
                result.messages.contains("RpcBidiService"),
            "Expected diagnostic mentioning Producer, method 'subscribe', and RpcBidiService; " +
                "got: ${result.messages}"
        )
    }

    @Test
    fun `RpcBidiService with everything compiles OK`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcBidiService
import com.monkopedia.ksrpc.RpcService
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
data class Update(val id: String)

@KsService
interface Callback : RpcService {
    @KsMethod("/notify")
    suspend fun notify(msg: String)
}

@KsService
interface SubService : RpcService {
    @KsMethod("/echo")
    suspend fun echo(input: String): String
}

@KsService
interface FullService : RpcBidiService {
    @KsMethod("/child")
    suspend fun child(input: String): SubService

    @KsMethod("/subscribe")
    suspend fun subscribe(cb: Callback): String

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
    fun `recursive - service returning a RpcBidiService sub-service only needs RpcHostService`() {
        // Returning a sub-service only requires HOST tier regardless of the sub-service's
        // own tier. The sub-service manages its own bidirectional connections.
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcBidiService
import com.monkopedia.ksrpc.RpcHostService
import com.monkopedia.ksrpc.RpcService
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
data class Update(val id: String)

@KsService
interface BidiSub : RpcBidiService {
    @KsMethod("/updates")
    suspend fun updates(filter: String): Flow<Update>
}

@KsService
interface Outer : RpcHostService {
    @KsMethod("/bidi")
    suspend fun bidi(input: String): BidiSub
}
"""
        )
        val result = compile(sourceFile = source)
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "Expected RpcHostService returning BidiSub to compile (sub-service manages " +
                "its own tier). messages: ${result.messages}"
        )
    }
}
