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
import com.monkopedia.ksrpc.annotation.KsService
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.builtins.serializer

@KsService
interface GenericEcho<T> : RpcService {
    @KsMethod("/echo")
    suspend fun echo(item: T): T

    @KsMethod("/maybe")
    suspend fun maybe(item: T?): T?
}

private class GenericEchoStringImpl : GenericEcho<String> {
    override suspend fun echo(item: String): String = "echoed:$item"
    override suspend fun maybe(item: String?): String? = if (item == null) null else "maybe:$item"
    override suspend fun close() = Unit
}

/**
 * End-to-end round-trip tests for a generic `@KsService` with `T` and `T?` method
 * signatures. Exercises the companion's `invoke(serializer)` factory, the generated
 * `Obj<T>` RpcObject, and the Stub's ability to push method calls over a SerializedService
 * channel using the injected serializer.
 *
 * This variant uses the reified helpers (`rpcObject<T>()`, `serialized<T>()`, `toStub<T>()`)
 * which for generic services go through the companion's `RpcObjectFactory` supertype and
 * the `typeOf<T>()` type arguments to reach an `RpcObject`.
 */
class GenericServiceRoundTripTest :
    RpcFunctionalityTest(
        serializedChannel = {
            val impl: GenericEcho<String> = GenericEchoStringImpl()
            impl.serialized<GenericEcho<String>, String>(ksrpcEnvironment { })
        },
        verifyOnChannel = { serializedChannel ->
            val stub = serializedChannel.toStub<GenericEcho<String>, String>()
            assertEquals("echoed:hi", stub.echo("hi"))
            assertEquals("maybe:hi", stub.maybe("hi"))
            assertNull(stub.maybe(null))
        }
    ) {
    @Test
    fun testDirectObj() = runBlockingUnit {
        val rpcObject: RpcObject<GenericEcho<String>> = GenericEcho(String.serializer())
        assertEquals(
            "com.monkopedia.ksrpc.GenericEcho",
            rpcObject.serviceName
        )
        val endpoints = rpcObject.endpoints
        assertEquals(true, "echo" in endpoints)
        assertEquals(true, "maybe" in endpoints)
    }

    @Test
    fun reifiedRpcObjectResolvesFactory() = runBlockingUnit {
        val rpcObject = rpcObject<GenericEcho<String>>()
        assertEquals("com.monkopedia.ksrpc.GenericEcho", rpcObject.serviceName)
        assertTrue("echo" in rpcObject.endpoints)
        assertTrue("maybe" in rpcObject.endpoints)
    }

    @Test
    fun reifiedSerializedAndToStubRoundTrip() = runBlockingUnit {
        val impl: GenericEcho<String> = GenericEchoStringImpl()
        val channel = impl.serialized<GenericEcho<String>, String>(ksrpcEnvironment { })
        val stub = channel.toStub<GenericEcho<String>, String>()
        assertEquals("echoed:hi", stub.echo("hi"))
        assertEquals("maybe:hi", stub.maybe("hi"))
        assertNull(stub.maybe(null))
    }

    /**
     * Explicit end-to-end round trip via `rpcObject<GenericEcho<String>>()`. Complements
     * [reifiedRpcObjectResolvesFactory] (which only inspects metadata) by actually sending
     * and receiving calls over a serialized channel built from the resolved `RpcObject`.
     */
    @Test
    fun reifiedRpcObjectRoundTrip() = runBlockingUnit {
        val rpcObject = rpcObject<GenericEcho<String>>()
        val impl: GenericEcho<String> = GenericEchoStringImpl()
        val channel = impl.serialized(rpcObject, ksrpcEnvironment { })
        val stub = rpcObject.createStub(channel)
        assertEquals("echoed:hi", stub.echo("hi"))
        assertEquals("maybe:hi", stub.maybe("hi"))
        assertNull(stub.maybe(null))
    }
}

/**
 * Validates the KType-based factory API that the generated companion exposes via
 * [RpcObjectFactory]. Callers without a static [kotlinx.serialization.KSerializer] — for
 * example reflective or introspection code — should still be able to reach an
 * [RpcObject] for a generic service by handing the companion `KType`s.
 */
private data class NotSerializable(val x: Int)

class GenericServiceFactoryTest {

    @Test
    fun arityMatchesTypeParameterCount() {
        val factory: RpcObjectFactory<*> = GenericEcho
        assertEquals(1, factory.arity)
    }

    @Test
    fun companionIsAccessibleViaFactoryInterface() {
        // This is the "registry" use case: store the companion by its factory supertype
        // and materialize an RpcObject for concrete type arguments later.
        val factory: RpcObjectFactory<*> = GenericEcho
        val rpcObject = factory.create(listOf(typeOf<String>()))
        assertEquals("com.monkopedia.ksrpc.GenericEcho", rpcObject.serviceName)
    }

    @Test
    fun createProducesWorkingRpcObject() = runBlockingUnit {
        val rpcObject = GenericEcho.create(listOf(typeOf<String>()))
        assertEquals("com.monkopedia.ksrpc.GenericEcho", rpcObject.serviceName)
        val endpoints = rpcObject.endpoints
        assertTrue("echo" in endpoints)
        assertTrue("maybe" in endpoints)
    }

    @Test
    fun createRoundTripsThroughPipe() = runBlockingUnit {
        @Suppress("UNCHECKED_CAST")
        val rpcObject =
            GenericEcho.create(listOf(typeOf<String>())) as RpcObject<GenericEcho<String>>
        val impl: GenericEcho<String> = object : GenericEcho<String> {
            override suspend fun echo(item: String): String = "echoed:$item"
            override suspend fun maybe(item: String?): String? =
                if (item == null) null else "maybe:$item"
            override suspend fun close() = Unit
        }
        val channel = impl.serialized(rpcObject, ksrpcEnvironment { })
        val stub: GenericEcho<String> = rpcObject.createStub(channel)
        assertEquals("echoed:hi", stub.echo("hi"))
        assertEquals("maybe:hi", stub.maybe("hi"))
        assertNull(stub.maybe(null))
    }

    @Test
    fun createWithTooFewTypeArgsThrows() {
        val ex = assertFailsWith<IllegalArgumentException> {
            GenericEcho.create(emptyList())
        }
        val message = ex.message.orEmpty()
        assertTrue(
            "GenericEcho" in message,
            "expected message to name the service; got: $message"
        )
    }

    @Test
    fun createWithTooManyTypeArgsThrows() {
        val ex = assertFailsWith<IllegalArgumentException> {
            GenericEcho.create(listOf(typeOf<String>(), typeOf<Int>()))
        }
        val message = ex.message.orEmpty()
        assertTrue(
            "GenericEcho" in message,
            "expected message to name the service; got: $message"
        )
    }

    @Test
    fun createWithNonSerializableTypeThrows() {
        val ex = assertFailsWith<IllegalArgumentException> {
            GenericEcho.create(listOf(typeOf<NotSerializable>()))
        }
        val message = ex.message.orEmpty()
        assertTrue(
            "NotSerializable" in message,
            "expected message to name the offending type; got: $message"
        )
        assertTrue(
            "GenericEcho" in message,
            "expected message to name the service; got: $message"
        )
    }
}

