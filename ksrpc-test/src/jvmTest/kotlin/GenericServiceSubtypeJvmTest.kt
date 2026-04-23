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
 * The supported pattern for subtype-specialization today is to rely on the parent's
 * `@KsService` annotation and reach the parent's `RpcObjectFactory` companion via
 * `rpcObject()` supertype walking. The resulting `RpcObject`/`Stub` is the parent's
 * (e.g. `GenericEcho<String>`), not the subtype's — so callers cannot expect a
 * `rpcObject<TypedGenericEchoJvm>()` result to be usable as a stub for
 * `TypedGenericEchoJvm` directly. Generating a dedicated companion/stub for
 * `@KsService` subtypes of generic services is tracked as a follow-up.
 *
 * JVM-only: the kotlin.reflect-based supertype walk in `rpcObject()`'s JVM actual picks
 * up `GenericEcho<String>` from `TypedGenericEchoJvm`'s supertypes with `String`
 * preserved in `KType.arguments`, then feeds that to `GenericEcho.create(...)`. On
 * Native/JS/WASM `findAssociatedObject<RpcObjectKey>()` returns the parent's factory but
 * `typeOf<Subtype>().arguments` is empty (the subtype has no type params), so the
 * resulting `factory.create(emptyList())` throws an arity mismatch. Lifting this path
 * to all platforms requires either new FIR-phase declaration generation on the subtype
 * (to synthesize a per-subtype companion with baked-in type args) or runtime access to
 * the subtype's supertype `KType` without `kotlin.reflect.full.allSupertypes` (which
 * isn't available on Native/JS/WASM). Tracked as a follow-up off issue #64.
 *
 * Cross-platform callers should today use the `RpcObjectFactory` route instead —
 * `GenericEcho.create(listOf(typeOf<String>()))` works on every platform (see
 * `GenericServiceSubtypeTest.scenario1_factoryCreateWithConcreteArg`).
 */
private interface TypedGenericEchoJvm : GenericEcho<String>

private class TypedGenericEchoJvmImpl : GenericEcho<String> {
    override suspend fun echo(item: String): String = "typed-echo:$item"
    override suspend fun maybe(item: String?): String? = item?.let { "typed-maybe:$it" }
    override suspend fun close() = Unit
}

class GenericServiceSubtypeJvmTest {

    /**
     * `rpcObject<TypedGenericEchoJvm>()` should find no direct companion on
     * `TypedGenericEchoJvm`, walk its supertypes, discover `GenericEcho`'s
     * `RpcObjectFactory` companion, extract `String` from the `GenericEcho<String>`
     * supertype, and return a working `RpcObject` for that specialization.
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
