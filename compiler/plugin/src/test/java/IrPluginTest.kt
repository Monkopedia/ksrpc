/*
 * Copyright 2021 Jason Monk
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
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.junit.Test

class IrPluginTest {
    val sourceFile = SourceFile.kotlin(
        "main.kt",
        """
import com.monkopedia.ksrpc.KsMethod
import com.monkopedia.ksrpc.KsService
import com.monkopedia.ksrpc.RpcService

@KsService
interface MyInterface: RpcService {

    @KsMethod("/serial_name")
    suspend fun doSomething(input: String): Int
}

fun main() {
  println(debug())
}
fun debug() = "Hello, World!"
"""
    )

    @Test
    fun `IR plugin success`() {
        val result = compile(sourceFile = sourceFile)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        assertEquals("", result.outputDirectory.absolutePath)
    }

    @Test
    fun `IR plugin method detection`() {
        val result = compile(sourceFile = sourceFile)
        assertContains(result.messages, "Generating for class MyInterface with 1 methods")
    }
}

fun compile(
    sourceFiles: List<SourceFile>,
    plugin: ComponentRegistrar = KsrpcComponentRegistrar(),
): KotlinCompilation.Result {
    return KotlinCompilation().apply {
        sources = sourceFiles
        useIR = true
        compilerPlugins = listOf(plugin)
        inheritClassPath = true
    }.compile()
}

fun compile(
    sourceFile: SourceFile,
    plugin: ComponentRegistrar = KsrpcComponentRegistrar(),
): KotlinCompilation.Result {
    return compile(listOf(sourceFile), plugin)
}