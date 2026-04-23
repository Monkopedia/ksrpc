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
@file:OptIn(KsrpcInternal::class)

package com.monkopedia.ksrpc

import com.monkopedia.ksrpc.annotation.KsrpcInternal
import com.monkopedia.ksrpc.channels.SerializedService
import com.monkopedia.ksrpc.internal.ServiceExecutor
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

@OptIn(KsrpcInternal::class)
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
                },
                metadata = emptyList()
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
                },
                metadata = emptyList()
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
                },
                metadata = emptyList()
            )

        val found = method.findSubserviceTransformers()

        assertEquals(2, found.size)
        assertSame(subserviceIn, found[0])
        assertSame(subserviceOut, found[1])
    }

    /**
     * Custom adapter transformers built on [BaseSubserviceTransformer] (the
     * pattern used by ksrpc-flow's `FlowSubserviceTransformer` to adapt
     * `Flow<T>` onto a `KsFlowService<T>` sub-service) surface directly from
     * [RpcMethod.findSubserviceTransformers] via the shared base class — no
     * delegation peel required — so introspection resolves them to the
     * underlying sub-service name.
     */
    @Test
    fun findSubserviceTransformersMatchesBaseSubserviceTransformerSubclasses() {
        val adapter = object : BaseSubserviceTransformer<IntrospectionSubservice, String>() {
            override val serviceObject: RpcObject<IntrospectionSubservice> =
                IntrospectionSubserviceObject

            override fun toService(value: String): IntrospectionSubservice = error("not used")
            override fun fromService(service: IntrospectionSubservice): String = error("not used")
        }

        val method =
            RpcMethod<RpcService, String, String>(
                endpoint = "/adapter",
                inputTransform = adapter,
                outputTransform = adapter,
                method = object : ServiceExecutor {
                    override suspend fun invoke(service: RpcService, input: Any?): Any? = input
                },
                metadata = emptyList()
            )

        val found = method.findSubserviceTransformers()
        assertEquals(2, found.size)
        assertSame(adapter, found[0])
        assertSame(adapter, found[1])
        assertEquals(
            "IntrospectionSubservice",
            found[0].serviceObject.serviceName
        )
    }
}
