/**
 * Copyright (C) 2022 Jason Monk <monkopedia@gmail.com>
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
package com.monkopedia.ksrpc.plugin

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import kotlin.test.assertContains
import kotlin.test.assertEquals
import org.junit.Test

class InputTypesTest {
    val sourceFile = SourceFile.kotlin(
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

    @Test
    fun `Detects native types as default input`() {
        val result = compile(sourceFile = sourceFile)
        assertContains(
            result.messages,
            "generating MyInterface#Do1(\"native_input\") with types: DEFAULT(String) DEFAULT(Int)"
        )
    }

    @Test
    fun `Detects custom types as default input`() {
        val result = compile(sourceFile = sourceFile)
        assertContains(
            result.messages,
            "generating MyInterface#Do2(\"default_input\") " +
                "with types: DEFAULT(CustomType) DEFAULT(Int)"
        )
    }

    @Test
    fun `Detects binary input`() {
        val result = compile(sourceFile = sourceFile)
        assertContains(
            result.messages,
            "generating MyInterface#Do3(\"binary_input\") with " +
                "types: BINARY(ByteReadChannel) DEFAULT(Int)"
        )
    }

    @Test
    fun `Detects service input`() {
        val result = compile(sourceFile = sourceFile)
        assertContains(
            result.messages,
            "generating MyInterface#Do4(\"service_input\") with " +
                "types: SERVICE(MyInterface) DEFAULT(Int)"
        )
    }
}
