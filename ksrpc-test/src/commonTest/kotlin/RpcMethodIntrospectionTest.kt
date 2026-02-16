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

import com.monkopedia.ksrpc.channels.SerializedService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.serialization.builtins.serializer

private interface IntrospectionSubservice : RpcService

private object IntrospectionSubserviceObject : RpcObject<IntrospectionSubservice> {
    override val serviceName: String = "IntrospectionSubservice"
    override val endpoints: List<String> = emptyList()

    override fun <S> createStub(channel: SerializedService<S>): IntrospectionSubservice =
        error("unused")

    override fun findEndpoint(endpoint: String): RpcMethod<*, *, *> = error("unused")
}

class RpcMethodIntrospectionTest {

    @Test
    fun hasReturnTypeIsFalseForUnitOutputTransformer() {
        val method =
            RpcMethod<RpcService, String, Unit>(
                endpoint = "/notify",
                inputTransform = SerializerTransformer(String.serializer()),
                outputTransform = SerializerTransformer(Unit.serializer()),
                method = object : ServiceExecutor {
                    override suspend fun invoke(service: RpcService, input: Any?): Any? = Unit
                }
            )

        assertFalse(method.hasReturnType)
    }

    @Test
    fun hasReturnTypeIsTrueForNonUnitOutputTransformer() {
        val method =
            RpcMethod<RpcService, String, String>(
                endpoint = "/rpc",
                inputTransform = SerializerTransformer(String.serializer()),
                outputTransform = SerializerTransformer(String.serializer()),
                method = object : ServiceExecutor {
                    override suspend fun invoke(service: RpcService, input: Any?): Any? = "ok"
                }
            )

        assertTrue(method.hasReturnType)
    }

    @Test
    fun findSubserviceTransformersReturnsInputAndOutputTransformersInOrder() {
        val subserviceIn = SubserviceTransformer(IntrospectionSubserviceObject)
        val subserviceOut = SubserviceTransformer(IntrospectionSubserviceObject)
        val method =
            RpcMethod<RpcService, IntrospectionSubservice, IntrospectionSubservice>(
                endpoint = "/subservice",
                inputTransform = subserviceIn,
                outputTransform = subserviceOut,
                method = object : ServiceExecutor {
                    override suspend fun invoke(service: RpcService, input: Any?): Any? = input
                }
            )

        val found = method.findSubserviceTransformers()

        assertEquals(2, found.size)
        assertSame(subserviceIn, found[0])
        assertSame(subserviceOut, found[1])
    }
}
