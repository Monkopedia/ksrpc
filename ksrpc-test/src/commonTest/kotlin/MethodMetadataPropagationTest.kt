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
package com.monkopedia.ksrpc

import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsMethodMetadata
import com.monkopedia.ksrpc.annotation.KsService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Demo sibling annotation used purely to exercise the metadata propagation
 * machinery introduced in #11. This is NOT a shipped feature — real sibling
 * annotations (notification, timeout, error payload) live in follow-up issues.
 */
@KsMethodMetadata
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class KsTestMarker(
    val tag: String,
    val priority: Int = 0,
    val flags: Array<String> = []
)

/**
 * Second demo sibling annotation — verifies that multiple metadata annotations
 * on the same method are each captured.
 */
@KsMethodMetadata
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class KsTestFlag(val enabled: Boolean)

/**
 * A plain, non-metadata sibling annotation. The compiler plugin should ignore
 * it and NOT attach anything for it to the RpcMethod descriptor.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class NotMetadata(val s: String)

@KsService
interface AnnotationPropagationTestService : RpcService {
    @KsMethod("/plain")
    suspend fun plain(input: String): String

    @KsMethod("/with_marker")
    @KsTestMarker(tag = "hello", priority = 7, flags = ["a", "b"])
    suspend fun withMarker(input: String): String

    @KsMethod("/default_values")
    @KsTestMarker(tag = "bare")
    suspend fun withDefaultValues(input: String): String

    @KsMethod("/multiple")
    @KsTestMarker(tag = "multi")
    @KsTestFlag(enabled = true)
    suspend fun withMultiple(input: String): String

    @KsMethod("/non_metadata_sibling")
    @NotMetadata("ignored")
    suspend fun withNonMetadataSibling(input: String): String
}

class MethodMetadataPropagationTest {
    private val rpcObject = rpcObject<AnnotationPropagationTestService>()

    private fun method(endpoint: String): RpcMethod<*, *, *> = rpcObject.findEndpoint(endpoint)

    @Test
    fun plainMethodHasEmptyMetadata() {
        assertEquals(emptyList(), method("plain").metadata)
    }

    @Test
    fun siblingAnnotationIsCaptured() {
        val meta = method("with_marker").metadata(
            "com.monkopedia.ksrpc.KsTestMarker"
        )
        assertNotNull(meta)
        val tag = meta.argument("tag") as? MetadataValue.StringValue
        assertNotNull(tag)
        assertEquals("hello", tag.value)

        val priority = meta.argument("priority") as? MetadataValue.IntValue
        assertNotNull(priority)
        assertEquals(7, priority.value)

        val flags = meta.argument("flags") as? MetadataValue.ListValue
        assertNotNull(flags)
        assertEquals(
            listOf("a", "b"),
            flags.items.map { (it as MetadataValue.StringValue).value }
        )
    }

    @Test
    fun defaultValuedArgsAreNotCaptured() {
        // The plugin only captures arguments explicitly set at the call site.
        val meta = method("default_values").metadata(
            "com.monkopedia.ksrpc.KsTestMarker"
        )
        assertNotNull(meta)
        assertNotNull(meta.argument("tag"))
        // `priority` and `flags` used their defaults and are not captured.
        assertNull(meta.argument("priority"))
        assertNull(meta.argument("flags"))
    }

    @Test
    fun multipleSiblingAnnotationsAreAllCaptured() {
        val rpc = method("multiple")
        assertEquals(2, rpc.metadata.size)
        val marker = rpc.metadata("com.monkopedia.ksrpc.KsTestMarker")
        val flag = rpc.metadata("com.monkopedia.ksrpc.KsTestFlag")
        assertNotNull(marker)
        assertNotNull(flag)
        val enabled = flag.argument("enabled") as? MetadataValue.BooleanValue
        assertNotNull(enabled)
        assertTrue(enabled.value)
    }

    @Test
    fun annotationsWithoutKsMethodMetadataMarkerAreIgnored() {
        val rpc = method("non_metadata_sibling")
        // `@NotMetadata` is not annotated with `@KsMethodMetadata`, so it
        // must not appear in the captured metadata.
        assertEquals(emptyList(), rpc.metadata)
    }

    @Test
    fun metadataCarriesAnnotationFqName() {
        val meta = method("with_marker").metadata.single()
        assertEquals("com.monkopedia.ksrpc.KsTestMarker", meta.annotationFqName)
    }

    @Test
    fun rpcMethodMetadataDefaultsToEmpty() {
        // Forward/backward compatibility: a method with no sibling metadata
        // annotations still gets an empty (non-null) metadata list, and
        // looking up an absent annotation returns null rather than throwing.
        val rpc = method("plain")
        assertEquals(emptyList(), rpc.metadata)
        assertNull(rpc.metadata("com.example.NotRegistered"))
    }
}
