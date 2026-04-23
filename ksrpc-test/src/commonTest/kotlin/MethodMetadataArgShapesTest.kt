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
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exhaustive coverage of the annotation-argument shapes that the ksrpc compiler
 * plugin's `MetadataIrBuilder` claims to support: String/Int/Long/Boolean/Double/Float
 * constants, KClass literals, enum constants, and arrays (varargs) of any of the
 * above. `MethodMetadataPropagationTest` covers the high-level wiring; this file
 * focuses on per-shape fidelity (the right `MetadataValue` subtype and the right
 * content), default-value behavior, and large-literal round-tripping.
 */

// Primitives — one annotation per numeric type so the test can assert exactly
// one captured argument of exactly one subtype.
@KsMethodMetadata
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class KsLongMarker(val value: Long)

@KsMethodMetadata
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class KsDoubleMarker(val value: Double)

@KsMethodMetadata
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class KsFloatMarker(val value: Float)

// Mixed-shape annotation used to verify that multiple arguments of different
// MetadataValue subtypes coexist in declaration order.
@KsMethodMetadata
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class KsMixedMarker(
    val text: String,
    val count: Int,
    val ratio: Double,
    val enabled: Boolean,
    val payload: KClass<*>,
    val color: KsTestColor
)

// Arrays of non-String primitives. The common `MethodMetadataPropagationTest`
// only covers `Array<String>`, which doesn't exercise the Int/Long/Boolean/
// Double/Float branches of `buildMetadataValue` under `IrVararg`.
@KsMethodMetadata
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class KsIntArrayMarker(val values: IntArray)

@KsMethodMetadata
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class KsLongArrayMarker(val values: LongArray)

@KsMethodMetadata
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class KsBooleanArrayMarker(val values: BooleanArray)

// Arrays of reference types. `Array<out KClass<*>>` is how the Kotlin language
// spec actually spells an annotation-argument array of KClass literals, and it
// exercises the `IrVararg`+`IrClassReference` pairing in the plugin.
@KsMethodMetadata
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class KsKClassArrayMarker(val types: Array<KClass<*>>)

@KsMethodMetadata
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class KsEnumArrayMarker(val colors: Array<KsTestColor>)

// Default values — verifies the plugin's existing "do not capture defaults" rule
// holds for every shape the user can default.
@KsMethodMetadata
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class KsAllDefaults(
    val text: String = "default",
    val count: Int = 42,
    val big: Long = 1_000L,
    val ratio: Double = 1.5,
    val factor: Float = 2.5f,
    val enabled: Boolean = true,
    val colors: Array<KsTestColor> = []
)

// Demo payload types used as KClass args in this file.
class KsTestPayloadA
class KsTestPayloadB

@KsService
interface MetadataArgShapesService : RpcService {
    @KsMethod("/long_arg")
    @KsLongMarker(value = Long.MAX_VALUE)
    suspend fun longArg(input: String): String

    @KsMethod("/double_arg")
    @KsDoubleMarker(value = 3.141592653589793)
    suspend fun doubleArg(input: String): String

    @KsMethod("/float_arg")
    @KsFloatMarker(value = 2.71828f)
    suspend fun floatArg(input: String): String

    @KsMethod("/mixed")
    @KsMixedMarker(
        text = "hello",
        count = 99,
        ratio = 0.5,
        enabled = true,
        payload = KsTestPayloadA::class,
        color = KsTestColor.BLUE
    )
    suspend fun mixed(input: String): String

    @KsMethod("/int_array")
    @KsIntArrayMarker(values = [1, 2, 3])
    suspend fun intArray(input: String): String

    @KsMethod("/long_array")
    @KsLongArrayMarker(values = [1L, 2L, 3L])
    suspend fun longArray(input: String): String

    @KsMethod("/bool_array")
    @KsBooleanArrayMarker(values = [true, false, true])
    suspend fun boolArray(input: String): String

    @KsMethod("/kclass_array")
    @KsKClassArrayMarker(types = [KsTestPayloadA::class, KsTestPayloadB::class])
    suspend fun kClassArray(input: String): String

    @KsMethod("/enum_array")
    @KsEnumArrayMarker(colors = [KsTestColor.RED, KsTestColor.GREEN])
    suspend fun enumArray(input: String): String

    @KsMethod("/all_defaults_bare")
    @KsAllDefaults
    suspend fun allDefaultsBare(input: String): String

    @KsMethod("/all_defaults_override_one")
    @KsAllDefaults(count = 7)
    suspend fun allDefaultsOverrideOne(input: String): String

    @KsMethod("/large")
    @KsMixedMarker(
        // A 2 KiB string literal — well above anything a hand-written annotation
        // would carry, but still legal. Verifies the plugin does not truncate
        // and does not special-case size.
        text = LARGE_STRING,
        count = 1_000_000,
        ratio = Double.MAX_VALUE,
        enabled = false,
        payload = KsTestPayloadB::class,
        color = KsTestColor.RED
    )
    suspend fun large(input: String): String

    @KsMethod("/long_array_large")
    @KsLongArrayMarker(
        values = [
            0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L,
            10L, 11L, 12L, 13L, 14L, 15L, 16L, 17L, 18L, 19L,
            20L, 21L, 22L, 23L, 24L, 25L, 26L, 27L, 28L, 29L,
            30L, 31L, 32L, 33L, 34L, 35L, 36L, 37L, 38L, 39L
        ]
    )
    suspend fun longArrayLarge(input: String): String
}

// Used from the service interface — must be a compile-time constant and live
// outside the interface (Kotlin allows `const val` only in top-level/companion).
private const val LARGE_STRING =
    "0123456789abcdefghijklmnopqrstuvwxyz0123456789abcdefghijklmnopqrstuvwxyz" +
        "0123456789abcdefghijklmnopqrstuvwxyz0123456789abcdefghijklmnopqrstuvwxyz" +
        "0123456789abcdefghijklmnopqrstuvwxyz0123456789abcdefghijklmnopqrstuvwxyz" +
        "0123456789abcdefghijklmnopqrstuvwxyz0123456789abcdefghijklmnopqrstuvwxyz" +
        "0123456789abcdefghijklmnopqrstuvwxyz0123456789abcdefghijklmnopqrstuvwxyz" +
        "0123456789abcdefghijklmnopqrstuvwxyz0123456789abcdefghijklmnopqrstuvwxyz" +
        "0123456789abcdefghijklmnopqrstuvwxyz0123456789abcdefghijklmnopqrstuvwxyz" +
        "0123456789abcdefghijklmnopqrstuvwxyz0123456789abcdefghijklmnopqrstuvwxyz"

class MethodMetadataArgShapesTest {
    private val rpcObject = rpcObject<MetadataArgShapesService>()

    private fun method(endpoint: String): RpcMethod<*, *, *> = rpcObject.findEndpoint(endpoint)

    private fun meta(endpoint: String, fqName: String): MethodMetadata {
        val m = method(endpoint).metadata(fqName)
        assertNotNull(
            m,
            "expected $fqName metadata on /$endpoint, got ${method(endpoint).metadata}"
        )
        return m
    }

    // --- Primitives ----------------------------------------------------------

    @Test
    fun longArgIsCapturedAsLongValue() {
        val m = meta("long_arg", "com.monkopedia.ksrpc.KsLongMarker")
        val v = m.argument("value") as? MetadataValue.LongValue
        assertNotNull(v)
        assertEquals(Long.MAX_VALUE, v.value)
    }

    @Test
    fun doubleArgIsCapturedAsDoubleValue() {
        val m = meta("double_arg", "com.monkopedia.ksrpc.KsDoubleMarker")
        val v = m.argument("value") as? MetadataValue.DoubleValue
        assertNotNull(v)
        assertEquals(3.141592653589793, v.value)
    }

    @Test
    fun floatArgIsCapturedAsFloatValue() {
        val m = meta("float_arg", "com.monkopedia.ksrpc.KsFloatMarker")
        val v = m.argument("value") as? MetadataValue.FloatValue
        assertNotNull(v)
        assertEquals(2.71828f, v.value)
    }

    // --- Mixed-shape ---------------------------------------------------------

    @Test
    fun mixedArgsAreCapturedWithCorrectSubtypesAndOrder() {
        val m = meta("mixed", "com.monkopedia.ksrpc.KsMixedMarker")
        // Declaration order must be preserved so transport layers can rely on it.
        assertEquals(
            listOf("text", "count", "ratio", "enabled", "payload", "color"),
            m.arguments.map { it.first }
        )
        assertEquals("hello", (m.argument("text") as MetadataValue.StringValue).value)
        assertEquals(99, (m.argument("count") as MetadataValue.IntValue).value)
        assertEquals(0.5, (m.argument("ratio") as MetadataValue.DoubleValue).value)
        assertTrue((m.argument("enabled") as MetadataValue.BooleanValue).value)
        assertEquals(
            KsTestPayloadA::class,
            (m.argument("payload") as MetadataValue.KClassValue).kClass
        )
        assertEquals(
            KsTestColor.BLUE,
            (m.argument("color") as MetadataValue.EnumValue).value
        )
    }

    // --- Arrays of primitives ------------------------------------------------

    @Test
    fun intArrayArgIsCapturedAsListOfIntValues() {
        val m = meta("int_array", "com.monkopedia.ksrpc.KsIntArrayMarker")
        val list = m.argument("values") as? MetadataValue.ListValue
        assertNotNull(list)
        assertEquals(
            listOf(1, 2, 3),
            list.items.map { (it as MetadataValue.IntValue).value }
        )
    }

    @Test
    fun longArrayArgIsCapturedAsListOfLongValues() {
        val m = meta("long_array", "com.monkopedia.ksrpc.KsLongArrayMarker")
        val list = m.argument("values") as? MetadataValue.ListValue
        assertNotNull(list)
        assertEquals(
            listOf(1L, 2L, 3L),
            list.items.map { (it as MetadataValue.LongValue).value }
        )
    }

    @Test
    fun booleanArrayArgIsCapturedAsListOfBooleanValues() {
        val m = meta("bool_array", "com.monkopedia.ksrpc.KsBooleanArrayMarker")
        val list = m.argument("values") as? MetadataValue.ListValue
        assertNotNull(list)
        assertEquals(
            listOf(true, false, true),
            list.items.map { (it as MetadataValue.BooleanValue).value }
        )
    }

    // --- Arrays of reference types ------------------------------------------

    @Test
    fun kClassArrayArgIsCapturedAsListOfKClassValues() {
        val m = meta("kclass_array", "com.monkopedia.ksrpc.KsKClassArrayMarker")
        val list = m.argument("types") as? MetadataValue.ListValue
        assertNotNull(list)
        assertEquals(
            listOf(KsTestPayloadA::class, KsTestPayloadB::class),
            list.items.map { (it as MetadataValue.KClassValue).kClass }
        )
    }

    @Test
    fun enumArrayArgIsCapturedAsListOfEnumValues() {
        val m = meta("enum_array", "com.monkopedia.ksrpc.KsEnumArrayMarker")
        val list = m.argument("colors") as? MetadataValue.ListValue
        assertNotNull(list)
        assertEquals(
            listOf<Enum<*>>(KsTestColor.RED, KsTestColor.GREEN),
            list.items.map { (it as MetadataValue.EnumValue).value }
        )
    }

    // --- Defaults ------------------------------------------------------------

    @Test
    fun annotationWithAllDefaultsYieldsNoCapturedArguments() {
        // The plugin only captures arguments the caller explicitly set. When
        // every parameter is defaulted the entry is still present (so consumers
        // can tell the method was annotated) but carries no arguments.
        val m = meta("all_defaults_bare", "com.monkopedia.ksrpc.KsAllDefaults")
        assertEquals(emptyList(), m.arguments)
    }

    @Test
    fun defaultedArgumentsRemainUncapturedWhenOtherArgsAreOverridden() {
        val m = meta(
            "all_defaults_override_one",
            "com.monkopedia.ksrpc.KsAllDefaults"
        )
        val count = m.argument("count") as? MetadataValue.IntValue
        assertNotNull(count)
        assertEquals(7, count.value)
        // Every other arg used its default and must not appear.
        assertNull(m.argument("text"))
        assertNull(m.argument("big"))
        assertNull(m.argument("ratio"))
        assertNull(m.argument("factor"))
        assertNull(m.argument("enabled"))
        assertNull(m.argument("colors"))
    }

    // --- Large literals ------------------------------------------------------

    @Test
    fun largeStringAndExtremeNumericLiteralsRoundTrip() {
        val m = meta("large", "com.monkopedia.ksrpc.KsMixedMarker")
        assertEquals(
            LARGE_STRING,
            (m.argument("text") as MetadataValue.StringValue).value
        )
        assertEquals(1_000_000, (m.argument("count") as MetadataValue.IntValue).value)
        assertEquals(
            Double.MAX_VALUE,
            (m.argument("ratio") as MetadataValue.DoubleValue).value
        )
    }

    @Test
    fun largeLongArrayIsCapturedVerbatim() {
        val m = meta("long_array_large", "com.monkopedia.ksrpc.KsLongArrayMarker")
        val list = m.argument("values") as MetadataValue.ListValue
        assertEquals(40, list.items.size)
        assertEquals(0L, (list.items.first() as MetadataValue.LongValue).value)
        assertEquals(39L, (list.items.last() as MetadataValue.LongValue).value)
    }
}
