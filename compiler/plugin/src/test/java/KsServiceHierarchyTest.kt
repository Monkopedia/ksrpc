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
 * Compiler plugin tests for @KsService interface inheritance (hierarchy support).
 *
 * Currently @KsService on a subtype of another @KsService is rejected. These tests
 * document the intended behavior once the restriction is lifted. They are expected to
 * FAIL until the feature is implemented.
 */
class KsServiceHierarchyTest {

    @Test
    fun `KsService extending KsService compiles successfully`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService
import com.monkopedia.ksrpc.RpcBidiService
import kotlinx.coroutines.flow.Flow

@KsService
interface CoreService : RpcService {
    @KsMethod("/getData")
    suspend fun getData(id: String): String
}

@KsService
interface ExtendedService : CoreService, RpcBidiService {
    @KsMethod("/updates")
    suspend fun updates(filter: String): Flow<String>
}
"""
        )
        val result = compile(sourceFile = source)
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "Expected @KsService extending @KsService to compile. messages: ${result.messages}"
        )
    }

    @Test
    fun `linear chain A to B to C compiles`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService
import com.monkopedia.ksrpc.RpcHostService
import com.monkopedia.ksrpc.RpcBidiService
import kotlinx.coroutines.flow.Flow

@KsService
interface BaseService : RpcService {
    @KsMethod("/base")
    suspend fun base(): String
}

@KsService
interface MiddleService : BaseService, RpcHostService {
    @KsMethod("/middle")
    suspend fun middle(): BaseService
}

@KsService
interface FullService : MiddleService, RpcBidiService {
    @KsMethod("/stream")
    suspend fun stream(): Flow<String>
}
"""
        )
        val result = compile(sourceFile = source)
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "Expected linear chain to compile. messages: ${result.messages}"
        )
    }

    @Test
    fun `stub has all inherited methods`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService
import com.monkopedia.ksrpc.RpcBidiService
import com.monkopedia.ksrpc.channels.SerializedService
import kotlinx.coroutines.flow.Flow

@KsService
interface CoreService : RpcService {
    @KsMethod("/getData")
    suspend fun getData(id: String): String
}

@KsService
interface ExtendedService : CoreService, RpcBidiService {
    @KsMethod("/updates")
    suspend fun updates(filter: String): Flow<String>
}

// Verify the Stub can be instantiated and has both inherited and own methods
suspend fun useStub(channel: SerializedService<String>): String {
    val stub = ExtendedService.Stub(channel)
    val data = stub.getData("test")
    return data
}
"""
        )
        val result = compile(sourceFile = source)
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "Expected stub to have inherited methods. messages: ${result.messages}"
        )
    }

    @Test
    fun `endpoints list contains both inherited and own endpoints`() {
        // This test verifies the companion's rpcObject contains all endpoints.
        // The actual runtime assertion is in the integration test; here we verify
        // the code that accesses both endpoints compiles.
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService
import com.monkopedia.ksrpc.RpcBidiService
import com.monkopedia.ksrpc.rpcObject
import kotlinx.coroutines.flow.Flow

@KsService
interface CoreService : RpcService {
    @KsMethod("/getData")
    suspend fun getData(id: String): String
}

@KsService
interface ExtendedService : CoreService, RpcBidiService {
    @KsMethod("/updates")
    suspend fun updates(filter: String): Flow<String>
}

fun checkEndpoints() {
    val obj = rpcObject<ExtendedService>()
    val endpoints = obj.endpoints
    require("getData" in endpoints) { "Missing inherited endpoint getData" }
    require("updates" in endpoints) { "Missing own endpoint updates" }
}
"""
        )
        val result = compile(sourceFile = source)
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "Expected endpoints access to compile. messages: ${result.messages}"
        )
    }

    @Test
    fun `implementation class does not get multiple KsService supertypes error`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService
import com.monkopedia.ksrpc.RpcBidiService
import com.monkopedia.ksrpc.ksrpcEnvironment
import com.monkopedia.ksrpc.serialized
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@KsService
interface CoreService : RpcService {
    @KsMethod("/getData")
    suspend fun getData(id: String): String
}

@KsService
interface ExtendedService : CoreService, RpcBidiService {
    @KsMethod("/updates")
    suspend fun updates(filter: String): Flow<String>
}

class ExtendedServiceImpl : ExtendedService {
    override suspend fun getData(id: String): String = "data-${'$'}id"
    override suspend fun updates(filter: String): Flow<String> = flowOf("u1")
}

suspend fun roundTrip() {
    val impl = ExtendedServiceImpl()
    val channel = impl.serialized<ExtendedService, String>(ksrpcEnvironment { })
}
"""
        )
        val result = compile(sourceFile = source)
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "Expected implementation class to compile without errors. " +
                "messages: ${result.messages}"
        )
        assertTrue(
            !result.messages.contains("multiple @KsService supertypes"),
            "Should not get 'multiple @KsService supertypes' error. " +
                "messages: ${result.messages}"
        )
    }

    @Test
    fun `child with bidi methods must extend RpcBidiService`() {
        // Tier enforcement: if parent is RpcService but child adds Flow methods,
        // the child must extend RpcBidiService. This test verifies the tier check
        // still applies to inherited + own methods combined.
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService
import kotlinx.coroutines.flow.Flow

@KsService
interface CoreService : RpcService {
    @KsMethod("/getData")
    suspend fun getData(id: String): String
}

// This should fail: has Flow method but only extends RpcService (via CoreService)
@KsService
interface BadExtendedService : CoreService {
    @KsMethod("/updates")
    suspend fun updates(filter: String): Flow<String>
}
"""
        )
        val result = compile(sourceFile = source)
        // Should fail because Flow requires RpcBidiService
        assertTrue(
            result.exitCode != KotlinCompilation.ExitCode.OK,
            "Expected compilation to fail when Flow method lacks RpcBidiService tier. " +
                "messages: ${result.messages}"
        )
        assertTrue(
            result.messages.contains("RpcBidiService"),
            "Expected error to suggest RpcBidiService. messages: ${result.messages}"
        )
    }

    @Test
    fun `child with sub-service output must extend at least RpcHostService`() {
        // Tier enforcement: if child adds a sub-service-returning method, it needs
        // at least RpcHostService.
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

@KsService
interface LeafService : RpcService {
    @KsMethod("/ping")
    suspend fun ping(msg: String): String
}

@KsService
interface CoreService : RpcService {
    @KsMethod("/getData")
    suspend fun getData(id: String): String
}

// This should fail: has sub-service output but only extends RpcService (via CoreService)
@KsService
interface BadHostService : CoreService {
    @KsMethod("/getLeaf")
    suspend fun getLeaf(): LeafService
}
"""
        )
        val result = compile(sourceFile = source)
        assertTrue(
            result.exitCode != KotlinCompilation.ExitCode.OK,
            "Expected compilation to fail when sub-service output lacks RpcHostService tier. " +
                "messages: ${result.messages}"
        )
        assertTrue(
            result.messages.contains("RpcHostService"),
            "Expected error to suggest RpcHostService. messages: ${result.messages}"
        )
    }

    @Test
    fun `sub-service returning a hierarchy service compiles`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService
import com.monkopedia.ksrpc.RpcHostService
import com.monkopedia.ksrpc.RpcBidiService
import kotlinx.coroutines.flow.Flow

@KsService
interface CoreService : RpcService {
    @KsMethod("/getData")
    suspend fun getData(id: String): String
}

@KsService
interface ExtendedService : CoreService, RpcBidiService {
    @KsMethod("/updates")
    suspend fun updates(filter: String): Flow<String>
}

@KsService
interface ParentService : RpcHostService {
    @KsMethod("/getExtended")
    suspend fun getExtended(): ExtendedService
}
"""
        )
        val result = compile(sourceFile = source)
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "Expected sub-service returning hierarchy service to compile. " +
                "messages: ${result.messages}"
        )
    }

    @Test
    fun `parent service unaffected by child existing`() {
        // Verifying that the parent @KsService still compiles and generates correctly
        // when a child @KsService extends it in the same compilation unit.
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService
import com.monkopedia.ksrpc.RpcBidiService
import com.monkopedia.ksrpc.rpcObject
import com.monkopedia.ksrpc.channels.SerializedService
import kotlinx.coroutines.flow.Flow

@KsService
interface CoreService : RpcService {
    @KsMethod("/getData")
    suspend fun getData(id: String): String
}

@KsService
interface ExtendedService : CoreService, RpcBidiService {
    @KsMethod("/updates")
    suspend fun updates(filter: String): Flow<String>
}

// Verify CoreService still works independently
suspend fun useCoreStub(channel: SerializedService<String>): String {
    val stub = CoreService.Stub(channel)
    return stub.getData("test")
}

fun checkCoreEndpoints() {
    val obj = rpcObject<CoreService>()
    require("getData" in obj.endpoints)
}
"""
        )
        val result = compile(sourceFile = source)
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "Expected parent service to still work independently. " +
                "messages: ${result.messages}"
        )
    }
}
