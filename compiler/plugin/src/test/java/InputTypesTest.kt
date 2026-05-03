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

class InputTypesTest {
    val sourceFile =
        SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcBidiService
import com.monkopedia.ksrpc.RpcService
import io.ktor.utils.io.ByteReadChannel
import kotlinx.serialization.Serializable

@Serializable
data class CustomType(
    val someValue: String,
    val someOtherValue: Int
)

@KsService
interface MyInterface: RpcBidiService {

    @KsMethod("/native_input")
    suspend fun do1(input: String): Int
    @KsMethod("/default_input")
    suspend fun do2(input: CustomType): Int
    @KsMethod("/binary_input")
    suspend fun do3(input: ByteReadChannel): Int
    @KsMethod("/service_input")
    suspend fun do4(input: MyInterface): Int
}
"""
        )

    @Test
    fun `IR plugin success`() {
        val result = compile(sourceFile = sourceFile)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
    }
}
