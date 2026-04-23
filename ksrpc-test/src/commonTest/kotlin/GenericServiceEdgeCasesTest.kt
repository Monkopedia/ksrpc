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
import com.monkopedia.ksrpc.annotation.KsNotification
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.annotation.KsTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.builtins.serializer

/**
 * Edge-case coverage for generic `@KsService` — see issue #57.
 *
 * The existing [GenericServiceTest] covers the wrapper-type happy path (List/Set/Map/
 * nullable/nested) with a single type parameter. This file covers shapes that would
 * regress silently if serializer wiring drifted between FIR stub synthesis and IR body
 * generation:
 *
 * - Round-trip on a service with two type parameters where `K` and `V` are distinct
 *   types, so a swap of serializer slots would produce a deserialization error rather
 *   than accidentally working with structurally-equivalent types.
 * - `@KsTimeout`, `@KsNotification`, and user `@KsMethodMetadata`-marked annotations
 *   on generic methods propagate into the generated `RpcMethod`'s metadata list.
 * - `Map<K, V>` flipped to `Map<V, K>` in return position exercises the generic-path
 *   wrapper composition in both input and output with independent K/V slots.
 */

@KsService
interface KsPair<K, V> : RpcService {
    @KsMethod("/get")
    suspend fun get(key: K): V

    @KsMethod("/contains")
    suspend fun contains(key: K): Boolean

    @KsMethod("/firstKey")
    suspend fun firstKey(u: Unit): K

    @KsMethod("/firstValue")
    suspend fun firstValue(u: Unit): V
}

private class StringIntKsPairImpl(private val data: Map<String, Int>) : KsPair<String, Int> {
    override suspend fun get(key: String): Int = data.getValue(key)
    override suspend fun contains(key: String): Boolean = key in data
    override suspend fun firstKey(u: Unit): String = data.keys.first()
    override suspend fun firstValue(u: Unit): Int = data.values.first()
    override suspend fun close() = Unit
}

/**
 * Multi-type-parameter round trip. The generated `Stub` and `Obj` take two
 * `KSerializer` fields, one per service type parameter. This test uses `K=String`
 * and `V=Int` so that a swap of the serializer slots would surface as a decoding
 * failure rather than silently succeeding on structurally-compatible types.
 */
class GenericKsPairRoundTripTest :
    RpcFunctionalityTest(
        serializedChannel = {
            val impl: KsPair<String, Int> =
                StringIntKsPairImpl(mapOf("one" to 1, "two" to 2))
            impl.serialized<KsPair<String, Int>, String>(ksrpcEnvironment { })
        },
        verifyOnChannel = { serializedChannel ->
            val stub = serializedChannel.toStub<KsPair<String, Int>, String>()
            assertEquals(1, stub.get("one"))
            assertEquals(2, stub.get("two"))
            assertTrue(stub.contains("one"))
            assertEquals("one", stub.firstKey(Unit))
            assertEquals(1, stub.firstValue(Unit))
        }
    ) {
    @Test
    fun factoryExposesArityOfTwo() {
        val factory: RpcObjectFactory<*> = KsPair
        assertEquals(2, factory.arity)
    }

    @Test
    fun directObjWithTwoSerializers() = runBlockingUnit {
        val rpcObject: RpcObject<KsPair<String, Int>> =
            KsPair(String.serializer(), Int.serializer())
        assertEquals("com.monkopedia.ksrpc.KsPair", rpcObject.serviceName)
        assertTrue("get" in rpcObject.endpoints)
        assertTrue("firstKey" in rpcObject.endpoints)
        assertTrue("firstValue" in rpcObject.endpoints)
    }
}

/**
 * A generic service whose method signature references both type parameters in wrapper
 * shapes — `Map<K, V>` in and `Map<V, K>` out. Verifies the generic-path wrapper
 * composer wires K and V independently and in the right slots on both input and
 * output transforms. If the K/V serializer slots were swapped in either composition,
 * the `V`-keyed result would fail to deserialize back as `Map<V, K>`.
 */
@KsService
interface KsFlipMap<K, V> : RpcService {
    @KsMethod("/swap")
    suspend fun swap(x: Map<K, V>): Map<V, K>
}

private class StringIntKsFlipMapImpl : KsFlipMap<String, Int> {
    override suspend fun swap(x: Map<String, Int>): Map<Int, String> =
        x.entries.associate { (k, v) -> v to k }
    override suspend fun close() = Unit
}

class GenericKsFlipMapRoundTripTest :
    RpcFunctionalityTest(
        serializedChannel = {
            val impl: KsFlipMap<String, Int> = StringIntKsFlipMapImpl()
            impl.serialized<KsFlipMap<String, Int>, String>(ksrpcEnvironment { })
        },
        verifyOnChannel = { serializedChannel ->
            val stub = serializedChannel.toStub<KsFlipMap<String, Int>, String>()
            val flipped = stub.swap(mapOf("a" to 1, "b" to 2))
            assertEquals(mapOf(1 to "a", 2 to "b"), flipped)
        }
    )

/**
 * `@KsTimeout` on a method of a generic `@KsService`. The plugin captures sibling
 * `@KsMethodMetadata`-marked annotations as metadata entries on the generated
 * `RpcMethod`; the [RpcMethod.timeoutMillis] extension reads that metadata list.
 * Regressions would show up as a missing `com.monkopedia.ksrpc.annotation.KsTimeout`
 * metadata entry on the generic method's `RpcMethod`.
 */
@KsService
interface TimedGeneric<T> : RpcService {
    @KsMethod("/get")
    @KsTimeout(millis = 500)
    suspend fun get(x: T): T

    @KsMethod("/fast")
    @KsTimeout(seconds = 3)
    suspend fun fast(x: T): T

    @KsMethod("/noTimeout")
    suspend fun noTimeout(x: T): T
}

class TimedGenericMetadataTest {
    private val rpcObject = rpcObject<TimedGeneric<String>>()

    @Test
    fun timeoutMillisIsCapturedOnGenericMethod() {
        val method = rpcObject.findEndpoint("get")
        assertEquals(500L, method.timeoutMillis)
    }

    @Test
    fun timeoutSecondsIsCapturedOnGenericMethod() {
        val method = rpcObject.findEndpoint("fast")
        assertEquals(3_000L, method.timeoutMillis)
    }

    @Test
    fun timeoutAnnotationFqNameIsPresentOnGenericMethod() {
        val method = rpcObject.findEndpoint("get")
        val meta = method.metadata("com.monkopedia.ksrpc.annotation.KsTimeout")
        assertNotNull(meta)
        assertEquals("com.monkopedia.ksrpc.annotation.KsTimeout", meta.annotationFqName)
        assertEquals(
            500L,
            (meta.argument("millis") as? MetadataValue.LongValue)?.value
        )
    }

    @Test
    fun methodWithoutTimeoutHasNullTimeoutOnGenericService() {
        val method = rpcObject.findEndpoint("noTimeout")
        assertNull(method.timeoutMillis)
    }
}

/**
 * `@KsNotification` on a method of a generic `@KsService`. The plugin must emit the
 * `com.monkopedia.ksrpc.annotation.KsNotification` metadata entry on the generic
 * method's `RpcMethod`. For JSON-RPC capable transports this is what drives the
 * notify (no-response) path; for PIPE transports it is simply round-tripped.
 */
@KsService
interface NotifyGeneric<T> : RpcService {
    @KsMethod("/push")
    @KsNotification
    suspend fun push(x: T)

    @KsMethod("/plain")
    suspend fun plain(x: T)
}

class NotifyGenericMetadataTest {
    private val rpcObject = rpcObject<NotifyGeneric<String>>()

    @Test
    fun notificationMetadataIsCapturedOnGenericMethod() {
        val method = rpcObject.findEndpoint("push")
        val meta = method.metadata("com.monkopedia.ksrpc.annotation.KsNotification")
        assertNotNull(meta)
        assertEquals("com.monkopedia.ksrpc.annotation.KsNotification", meta.annotationFqName)
        assertEquals(emptyList(), meta.arguments)
    }

    @Test
    fun plainUnitMethodOnGenericServiceHasNoNotificationMetadata() {
        val method = rpcObject.findEndpoint("plain")
        assertNull(method.metadata("com.monkopedia.ksrpc.annotation.KsNotification"))
    }
}

private class NotifyGenericStringImpl(private val received: CompletableDeferred<String>) :
    NotifyGeneric<String> {
    override suspend fun push(x: String) {
        received.complete(x)
    }
    override suspend fun plain(x: String) = Unit
    override suspend fun close() = Unit
}

class NotifyGenericRoundTripTest {
    @Test
    fun notificationMethodRoundTripsPayloadOverPipe() = runBlockingUnit {
        val received = CompletableDeferred<String>()
        val impl: NotifyGeneric<String> = NotifyGenericStringImpl(received)
        val channel = impl.serialized<NotifyGeneric<String>, String>(ksrpcEnvironment { })
        val stub = channel.toStub<NotifyGeneric<String>, String>()
        stub.push("hello")
        assertEquals("hello", withTimeout(2_000) { received.await() })
    }
}

/**
 * User `@KsMethodMetadata`-marked annotations on a method of a generic `@KsService`.
 * Verifies that arbitrary metadata arguments (`String`, `Int`) survive the plugin's
 * metadata capture on the generic generation path — they reach the `RpcMethod`'s
 * metadata list with the correct `annotationFqName` and argument values.
 */
@KsMethodMetadata
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class GenericAudit(val level: String, val weight: Int = 1)

@KsService
interface AuditedGeneric<T> : RpcService {
    @KsMethod("/track")
    @GenericAudit(level = "high", weight = 5)
    suspend fun track(x: T)

    @KsMethod("/emit")
    @GenericAudit(level = "low")
    suspend fun emit(x: T): T
}

class AuditedGenericMetadataTest {
    private val rpcObject = rpcObject<AuditedGeneric<String>>()

    @Test
    fun metadataArgumentsArePropagatedOnGenericUnitMethod() {
        val method = rpcObject.findEndpoint("track")
        val meta = method.metadata("com.monkopedia.ksrpc.GenericAudit")
        assertNotNull(meta)
        val level = meta.argument("level") as? MetadataValue.StringValue
        assertNotNull(level)
        assertEquals("high", level.value)
        val weight = meta.argument("weight") as? MetadataValue.IntValue
        assertNotNull(weight)
        assertEquals(5, weight.value)
    }

    @Test
    fun metadataArgumentsArePropagatedOnGenericReturningMethod() {
        val method = rpcObject.findEndpoint("emit")
        val meta = method.metadata("com.monkopedia.ksrpc.GenericAudit")
        assertNotNull(meta)
        val level = meta.argument("level") as? MetadataValue.StringValue
        assertNotNull(level)
        assertEquals("low", level.value)
        // `weight` uses its default and is not captured, matching the behaviour
        // documented in MethodMetadataPropagationTest.defaultValuedArgsAreNotCaptured.
        assertNull(meta.argument("weight"))
    }
}

private class AuditedGenericStringImpl : AuditedGeneric<String> {
    override suspend fun track(x: String) = Unit
    override suspend fun emit(x: String): String = "emit:$x"
    override suspend fun close() = Unit
}

class AuditedGenericRoundTripTest :
    RpcFunctionalityTest(
        serializedChannel = {
            val impl: AuditedGeneric<String> = AuditedGenericStringImpl()
            impl.serialized<AuditedGeneric<String>, String>(ksrpcEnvironment { })
        },
        verifyOnChannel = { serializedChannel ->
            val stub = serializedChannel.toStub<AuditedGeneric<String>, String>()
            stub.track("hi")
            assertEquals("emit:hi", stub.emit("hi"))
        }
    )

/**
 * Plain-Kotlin subtype chain rooted at a generic `@KsService`. The `@KsService` lives at
 * the parent; the subtype chain extends it without adding its own `@KsService`. This
 * exercises the "deep supertype chain" flavour that IS supported today — the leaf
 * implementation still satisfies `GenericEcho<String>` and can be used via
 * `rpcObject<GenericEcho<String>>()` (which is the cross-platform path — subtype-based
 * `rpcObject<DeepChildGenericEcho>()` on JVM lives in `GenericServiceSubtypeJvmTest`).
 *
 * The audit in issue #57 (2) asked whether the generic-service plugin can handle a deep
 * supertype chain where `@KsService` is applied at the leaf and `RpcService` is reached
 * transitively. That shape is currently rejected by the plugin's direct-supertype
 * `RpcService` check — see the plugin-side compile test
 * `GenericServiceTest.generic ksservice reached through deep plain-Kotlin super chain`
 * `is rejected with diagnostic` and the related follow-up.
 */
interface DeepMiddleGenericEcho<T> : GenericEcho<T>
interface DeepChildGenericEcho<T> : DeepMiddleGenericEcho<T>

private class DeepChildGenericEchoStringImpl : DeepChildGenericEcho<String> {
    override suspend fun echo(item: String): String = "deep:$item"
    override suspend fun maybe(item: String?): String? = item?.let { "deep-maybe:$it" }
    override suspend fun close() = Unit
}

class DeepGenericSubtypeChainRoundTripTest {
    @Test
    fun implOfDeepSubtypeChainRoundTripsThroughParentGenericRpcObject() = runBlockingUnit {
        val rpcObject: RpcObject<GenericEcho<String>> = GenericEcho(String.serializer())
        val impl: GenericEcho<String> = DeepChildGenericEchoStringImpl()
        val channel = impl.serialized(rpcObject, ksrpcEnvironment { })
        val stub = rpcObject.createStub(channel)
        assertEquals("deep:hi", stub.echo("hi"))
        assertEquals("deep-maybe:hi", stub.maybe("hi"))
        assertNull(stub.maybe(null))
    }
}
