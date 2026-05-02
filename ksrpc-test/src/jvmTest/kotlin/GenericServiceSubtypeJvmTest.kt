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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A plain Kotlin subtype that specializes a generic `@KsService` parent. Deliberately NOT
 * annotated with `@KsService` — the compiler plugin's current IR-side validation rejects
 * `@KsService` subtypes whose only `RpcService` supertype is reached transitively through
 * another `@KsService` (the validation in `KsrpcIrGenerationExtension.validateClass`
 * looks only at direct super-types for `RpcService`/`IntrospectableRpcService`).
 *
 * The compiler plugin synthesizes an `RpcObjectFactory` companion (with arity 0) on this
 * subtype, so `rpcObject<TypedGenericEchoJvm>()` resolves on all platforms. The factory's
 * `create(emptyList())` constructs the parent's `Obj<String>` directly, returning the
 * parent's `RpcObject` — whose `createStub()` returns `GenericEcho.Stub<String>`. This
 * avoids the JVM bridge-method `ClassCastException` that would occur if the companion
 * directly implemented `RpcObject<TypedGenericEchoJvm>` (the parent's Stub doesn't
 * implement `TypedGenericEchoJvm`). See issue #136.
 */
internal interface TypedGenericEchoJvm : GenericEcho<String>

private class TypedGenericEchoJvmImpl : GenericEcho<String> {
    override suspend fun echo(item: String): String = "typed-echo:$item"
    override suspend fun maybe(item: String?): String? = item?.let { "typed-maybe:$it" }
    override suspend fun close() = Unit
}

class GenericServiceSubtypeJvmTest {

    /**
     * `rpcObject<TypedGenericEchoJvm>()` should find the synthesized `RpcObjectFactory`
     * companion on `TypedGenericEchoJvm`, call `create(emptyList())`, and get back the
     * parent's `RpcObject<GenericEcho<String>>` — a fully working `RpcObject` whose
     * `createStub` returns `GenericEcho.Stub<String>`.
     */
    @Test
    fun rpcObjectResolvesGenericSupertype() = runBlockingUnit {
        @Suppress("UNCHECKED_CAST")
        val obj = rpcObject<TypedGenericEchoJvm>() as RpcObject<GenericEcho<String>>
        // The returned RpcObject is for the parent generic service (GenericEcho<String>),
        // since that's the class that owns the companion.
        assertEquals("com.monkopedia.ksrpc.GenericEcho", obj.serviceName)
        assertTrue("echo" in obj.endpoints)
        assertTrue("maybe" in obj.endpoints)

        // Round-trip through the resolved RpcObject to confirm it's usable, not just
        // metadata-complete.
        val impl: GenericEcho<String> = TypedGenericEchoJvmImpl()
        val channel = impl.serialized(obj, ksrpcEnvironment { })
        val stub = obj.createStub(channel)
        assertEquals("typed-echo:hi", stub.echo("hi"))
        assertEquals("typed-maybe:hi", stub.maybe("hi"))
        assertNull(stub.maybe(null))
    }
}
