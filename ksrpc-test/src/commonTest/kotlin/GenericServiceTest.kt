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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
 */
class GenericServiceRoundTripTest :
    RpcFunctionalityTest(
        serializedChannel = {
            val rpcObject: RpcObject<GenericEcho<String>> = GenericEcho(String.serializer())
            val impl: GenericEcho<String> = GenericEchoStringImpl()
            impl.serialized(rpcObject, ksrpcEnvironment { })
        },
        verifyOnChannel = { serializedChannel ->
            val rpcObject: RpcObject<GenericEcho<String>> = GenericEcho(String.serializer())
            val stub: GenericEcho<String> = rpcObject.createStub(serializedChannel)
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
}
