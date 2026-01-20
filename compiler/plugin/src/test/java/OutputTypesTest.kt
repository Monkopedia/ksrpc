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
import kotlin.test.assertContains
import kotlin.test.assertEquals
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Test

class OutputTypesTest {
    val sourceFile =
        SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService
import io.ktor.utils.io.ByteReadChannel
import kotlinx.serialization.Serializable

@Serializable
data class CustomType(
    val someValue: String,
    val someOtherValue: Int
)

@KsService
interface MyInterface: RpcService {

    @KsMethod("/native_output")
    suspend fun do1(input: String): Int
    @KsMethod("/default_output")
    suspend fun do2(input: String): CustomType
    @KsMethod("/binary_output")
    suspend fun do3(input: String): ByteReadChannel
    @KsMethod("/service_output")
    suspend fun do4(input: String): MyInterface
}
"""
        )

    @Test
    fun `IR plugin success`() {
        val result = compile(sourceFile = sourceFile)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
    }

    @Test
    fun `Detects native types as default output`() {
        val result = compile(sourceFile = sourceFile)
        assertContains(
            result.messages,
            "generating MyInterface#Do1(\"native_output\") with types: DEFAULT(String) DEFAULT(Int)"
        )
    }

    @Test
    fun `Detects custom types as default output`() {
        val result = compile(sourceFile = sourceFile)
        assertContains(
            result.messages,
            "generating MyInterface#Do2(\"default_output\") with " +
                "types: DEFAULT(String) DEFAULT(CustomType)"
        )
    }

    @Test
    fun `Detects binary output`() {
        val result = compile(sourceFile = sourceFile)
        assertContains(
            result.messages,
            "generating MyInterface#Do3(\"binary_output\") with " +
                "types: DEFAULT(String) BINARY(ByteReadChannel)"
        )
    }

    @Test
    fun `Detects service output`() {
        val result = compile(sourceFile = sourceFile)
        assertContains(
            result.messages,
            "generating MyInterface#Do4(\"service_output\") with " +
                "types: DEFAULT(String) SERVICE(MyInterface)"
        )
    }
}
