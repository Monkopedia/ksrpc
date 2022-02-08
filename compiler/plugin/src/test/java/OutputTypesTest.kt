package com.monkopedia.ksrpc.plugin

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class OutputTypesTest {
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
            "generating MyInterface#Do2(\"default_output\") with types: DEFAULT(String) DEFAULT(CustomType)"
        )
    }

    @Test
    fun `Detects binary output`() {
        val result = compile(sourceFile = sourceFile)
        assertContains(
            result.messages,
            "generating MyInterface#Do3(\"binary_output\") with types: DEFAULT(String) BINARY(ByteReadChannel)"
        )
    }

    @Test
    fun `Detects service output`() {
        val result = compile(sourceFile = sourceFile)
        assertContains(
            result.messages,
            "generating MyInterface#Do4(\"service_output\") with types: DEFAULT(String) SERVICE(MyInterface)"
        )
    }
}
