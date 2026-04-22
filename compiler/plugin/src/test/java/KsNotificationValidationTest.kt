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

class KsNotificationValidationTest {

    // Both @KsMethodMetadata and @KsNotification are defined inline because
    // the compiler plugin tests use an included build that references a
    // published ksrpc-core version which may predate the metadata
    // infrastructure. The annotation just needs the correct FQ name so the
    // compiler plugin recognizes it.
    private val annotations = SourceFile.kotlin(
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

@KsMethodMetadata
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class KsTimeout(val value: Long)
"""
    )

    @Test
    fun `KsNotification on Unit-returning method compiles successfully`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsNotification
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

@KsService
interface TestService : RpcService {
    @KsMethod("/notify")
    @KsNotification
    suspend fun notify(input: String)
}
"""
        )
        val result = compile(listOf(annotations, source))
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
    }

    @Test
    fun `multiple KsMethodMetadata-marked annotations on a single method compile`() {
        // The metadata IR builder walks every `@KsMethodMetadata`-marked sibling annotation
        // on the method and emits one MethodMetadata entry per annotation. Regressions where
        // the plugin only honors the first metadata annotation, or rejects combinations,
        // would break composite cases like `@KsNotification @KsTimeout` on the same method.
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsNotification
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.annotation.KsTimeout
import com.monkopedia.ksrpc.RpcService

@KsService
interface MultiMetadataService : RpcService {
    @KsMethod("/ping")
    @KsNotification
    @KsTimeout(500L)
    suspend fun ping(input: String)
}
"""
        )
        val result = compile(listOf(annotations, source))
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "Expected compilation to succeed with multiple metadata annotations; " +
                "messages: ${result.messages}"
        )
    }

    @Test
    fun `KsNotification on non-Unit-returning method fails compilation`() {
        val source = SourceFile.kotlin(
            "main.kt",
            """
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsNotification
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService

@KsService
interface BadService : RpcService {
    @KsMethod("/bad_notify")
    @KsNotification
    suspend fun badNotify(input: String): String
}
"""
        )
        val result = compile(listOf(annotations, source))
        assertTrue(
            result.exitCode != KotlinCompilation.ExitCode.OK,
            "Expected compilation to fail but got: ${result.exitCode}"
        )
        assertTrue(
            result.messages.contains("@KsNotification methods must return Unit"),
            "Expected error message about @KsNotification return type, got: ${result.messages}"
        )
    }
}
