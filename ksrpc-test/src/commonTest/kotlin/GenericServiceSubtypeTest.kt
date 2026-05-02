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

import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Cross-platform coverage for the "plain Kotlin subtype of generic `@KsService`" pattern
 * (issue #64 / #95).
 *
 * Two shapes of subtype are considered:
 * - Scenario 1 — non-generic subtype of a generic service (concrete type arg baked in):
 *   `interface TypedGenericEchoSubtype : GenericEcho<String>`
 * - Scenario 2 — generic subtype of a generic service (type param forwarded):
 *   `interface TypedGenericEchoT<T> : GenericEcho<T>`
 *
 * Issue #95 added compiler plugin support to synthesize companion objects on these
 * subtypes, so `rpcObject<TypedGenericEchoSubtype>()` and
 * `rpcObject<TypedGenericEchoT<String>>()` now work on all platforms (JVM, Native,
 * JS, WASM).
 *
 * The factory-based workaround tests (scenario1_factoryCreateWithConcreteArg,
 * scenario2_factoryCreateForwardedTypeArg) remain to ensure backwards compatibility.
 */
internal interface TypedGenericEchoSubtype : GenericEcho<String>

internal interface TypedGenericEchoT<T> : GenericEcho<T>

private class TypedGenericEchoImpl : GenericEcho<String> {
    override suspend fun echo(item: String): String = "typed-echo:$item"
    override suspend fun maybe(item: String?): String? = item?.let { "typed-maybe:$it" }
    override suspend fun close() = Unit
}

class GenericServiceSubtypeTest {

    /**
     * Scenario 1 workaround: call the parent's [RpcObjectFactory.create] with the concrete
     * type arg. Works on every platform because it bypasses `rpcObject<Subtype>()` and its
     * platform-dependent supertype walking.
     */
    @Test
    fun scenario1_factoryCreateWithConcreteArg() = runBlockingUnit {
        @Suppress("UNCHECKED_CAST")
        val obj = GenericEcho.create(listOf(typeOf<String>())) as RpcObject<GenericEcho<String>>
        assertEquals("com.monkopedia.ksrpc.GenericEcho", obj.serviceName)
        assertTrue("echo" in obj.endpoints)
        assertTrue("maybe" in obj.endpoints)

        val impl: GenericEcho<String> = TypedGenericEchoImpl()
        val channel = impl.serialized(obj, ksrpcEnvironment { })
        val stub = obj.createStub(channel)
        assertEquals("typed-echo:hi", stub.echo("hi"))
        assertEquals("typed-maybe:hi", stub.maybe("hi"))
        assertNull(stub.maybe(null))
    }

    /**
     * Scenario 2 workaround: the caller supplies the concrete type arg explicitly to the
     * parent factory. This is equivalent to specializing `TypedGenericEchoT<String>` — the
     * stub/obj types resolve to `GenericEcho<String>` either way because that's the type
     * the compiler plugin actually codegens a factory for.
     */
    @Test
    fun scenario2_factoryCreateForwardedTypeArg() = runBlockingUnit {
        @Suppress("UNCHECKED_CAST")
        val obj = GenericEcho.create(listOf(typeOf<String>())) as RpcObject<GenericEcho<String>>
        val impl: GenericEcho<String> = TypedGenericEchoImpl()
        val channel = impl.serialized(obj, ksrpcEnvironment { })
        val stub = obj.createStub(channel)
        assertEquals("typed-echo:x", stub.echo("x"))
    }

    /**
     * Regression: an ordinary (non-subtype) generic service resolution via the factory
     * continues to work. Guards against changes that would regress the common path while
     * investigating subtype handling.
     */
    @Test
    fun regression_factoryCreateForDirectGenericService() = runBlockingUnit {
        val obj = GenericEcho.create(listOf(typeOf<String>()))
        assertEquals("com.monkopedia.ksrpc.GenericEcho", obj.serviceName)
        assertTrue("echo" in obj.endpoints)
        assertTrue("maybe" in obj.endpoints)
    }

    /**
     * Scenario 1 — rpcObject<TypedGenericEchoSubtype>() resolves the companion synthesized
     * by the compiler plugin (#95). Works on all platforms via @RpcObjectKey.
     */
    @Test
    fun scenario1_rpcObjectOnNonGenericSubtype() = runBlockingUnit {
        val obj = rpcObject<TypedGenericEchoSubtype>()
        assertEquals("com.monkopedia.ksrpc.GenericEcho", obj.serviceName)
        assertTrue("echo" in obj.endpoints)
        assertTrue("maybe" in obj.endpoints)
    }

    /**
     * Scenario 1 — full round trip through a serialized channel using rpcObject<Subtype>().
     * The RpcObject delegates to the parent GenericEcho's Obj, so the stub implements
     * GenericEcho<String> (not TypedGenericEchoSubtype). Use the parent's factory directly
     * to get a properly typed RpcObject for the round-trip.
     */
    @Test
    fun scenario1_rpcObjectRoundTrip() = runBlockingUnit {
        // Verify the subtype companion resolves; then use the parent factory for round-trip
        val subtypeObj = rpcObject<TypedGenericEchoSubtype>()
        assertEquals("com.monkopedia.ksrpc.GenericEcho", subtypeObj.serviceName)

        @Suppress("UNCHECKED_CAST")
        val obj = GenericEcho.create(listOf(typeOf<String>())) as RpcObject<GenericEcho<String>>
        val impl: GenericEcho<String> = TypedGenericEchoImpl()
        val channel = impl.serialized(obj, ksrpcEnvironment { })
        val stub = obj.createStub(channel)
        assertEquals("typed-echo:hello", stub.echo("hello"))
        assertEquals("typed-maybe:world", stub.maybe("world"))
        assertNull(stub.maybe(null))
    }

    /**
     * Scenario 2 — rpcObject<TypedGenericEchoT<String>>() resolves the factory companion
     * synthesized by the compiler plugin (#95). Works on all platforms via @RpcObjectKey.
     */
    @Test
    fun scenario2_rpcObjectOnGenericSubtype() = runBlockingUnit {
        val obj = rpcObject<TypedGenericEchoT<String>>()
        assertEquals("com.monkopedia.ksrpc.GenericEcho", obj.serviceName)
        assertTrue("echo" in obj.endpoints)
        assertTrue("maybe" in obj.endpoints)
    }

    /**
     * Scenario 2 — full round trip through a serialized channel using
     * rpcObject<TypedGenericEchoT<String>>(). The stub is GenericEcho.Stub<String>,
     * so we cast to the parent type for the round-trip.
     */
    @Test
    fun scenario2_rpcObjectRoundTrip() = runBlockingUnit {
        @Suppress("UNCHECKED_CAST")
        val obj = rpcObject<TypedGenericEchoT<String>>() as RpcObject<GenericEcho<String>>
        val impl: GenericEcho<String> = TypedGenericEchoImpl()
        val channel = impl.serialized(obj, ksrpcEnvironment { })
        val stub = obj.createStub(channel)
        assertEquals("typed-echo:abc", stub.echo("abc"))
        assertEquals("typed-maybe:def", stub.maybe("def"))
        assertNull(stub.maybe(null))
    }
}
