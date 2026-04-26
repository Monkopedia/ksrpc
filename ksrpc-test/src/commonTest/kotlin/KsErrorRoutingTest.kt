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

import com.monkopedia.ksrpc.annotation.KsError
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.annotation.KsrpcInternal
import com.monkopedia.ksrpc.channels.CallData
import com.monkopedia.ksrpc.internal.HostSerializedChannelImpl
import com.monkopedia.ksrpc.internal.asClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

/**
 * Tests for #78 — runtime error routing for `@KsError` mappings. The server-side encode
 * path lives on [RpcMethod.encodeError]: it resolves a thrown KsrpcException's
 * `data::class` to the @KsError-bound code via [RpcMethod.reverseErrorMap] and embeds the
 * typed payload inside a [CallData.Error] frame whose `errorData` is the wire-format-encoded
 * payload. The client-side decode path lives on [RpcMethod.decodeError] (called from
 * [RpcMethod.callChannel] on receive): it consults [RpcMethod.forwardErrorMap] and rebuilds
 * a typed [KsrpcException] with the deserialized payload, falling back to plain
 * [RpcException] when no mapping resolves.
 *
 * The variant ([CallData.Error]) is the discriminator on the wire — there is no
 * `isError(callData)` round-trip through the serializer anymore, and transports natively
 * encode/decode the error variant rather than smuggling errors through `Serialized`.
 */

@Serializable
data class InitErrorPayload(val retry: Boolean, val reason: String)

@Serializable
data class VersionErrorPayload(val expected: Int, val actual: Int)

@Serializable
sealed class CategoryError {
    abstract val tag: String
}

@Serializable
@SerialName("retry")
data class RetryableError(override val tag: String, val backoffMs: Long) : CategoryError()

@Serializable
@SerialName("permanent")
data class PermanentError(override val tag: String) : CategoryError()

@KsService
interface TypedErrorService : RpcService {
    @KsMethod("/init")
    @KsError(code = 100, type = InitErrorPayload::class)
    @KsError(code = 101, type = VersionErrorPayload::class)
    suspend fun init(input: String): String

    @KsMethod("/plain")
    suspend fun plain(input: String): String

    /**
     * Bound to the sealed base [CategoryError]. Throwing any subclass instance
     * (e.g. [RetryableError], [PermanentError]) should match this binding via
     * the assignable-class lookup in [com.monkopedia.ksrpc.RpcMethod.encodeError].
     */
    @KsMethod("/category")
    @KsError(code = 200, type = CategoryError::class)
    suspend fun category(input: String): String
}

class KsErrorRoutingTest {

    /**
     * Server throws a typed KsrpcException whose data is bound via @KsError; client receives
     * a KsrpcException with the matching code and the deserialized typed data payload.
     */
    @Test
    fun mappedThrowDeliversTypedDataToClient() = runBlockingUnit {
        val service = object : TypedErrorService {
            override suspend fun init(input: String): String {
                throw KsrpcException(
                    code = 100,
                    message = "init failed",
                    data = InitErrorPayload(retry = true, reason = "bad input: $input")
                )
            }

            override suspend fun plain(input: String): String = error("unused")
            override suspend fun category(input: String): String = error("unused")
        }
        withInProcessChannel(service) { stub ->
            try {
                stub.init("payload")
                fail("Expected KsrpcException")
            } catch (t: Throwable) {
                assertIs<KsrpcException>(t)
                // The throw is rebuilt on the client into a fresh KsrpcException carrying
                // the mapped code and the deserialized typed payload — subtype identity is
                // not round-tripped per the design (#78).
                assertEquals(100, t.code)
                val payload = t.data
                assertIs<InitErrorPayload>(payload)
                assertEquals(true, payload.retry)
                assertEquals("bad input: payload", payload.reason)
            }
        }
    }

    /**
     * Multiple @KsError bindings on a single method: each typed throw resolves to its own
     * code + deserialized payload independently.
     */
    @Test
    fun secondMappingResolvesIndependently() = runBlockingUnit {
        val service = object : TypedErrorService {
            override suspend fun init(input: String): String {
                throw KsrpcException(
                    code = 101,
                    message = "version mismatch",
                    data = VersionErrorPayload(expected = 7, actual = 3)
                )
            }

            override suspend fun plain(input: String): String = error("unused")
            override suspend fun category(input: String): String = error("unused")
        }
        withInProcessChannel(service) { stub ->
            try {
                stub.init("anything")
                fail("Expected KsrpcException")
            } catch (t: Throwable) {
                assertIs<KsrpcException>(t)
                assertEquals(101, t.code)
                val payload = t.data
                assertIs<VersionErrorPayload>(payload)
                assertEquals(7, payload.expected)
                assertEquals(3, payload.actual)
            }
        }
    }

    /**
     * If the user constructs a KsrpcException with a code that differs from the
     * @KsError-bound code for the data's type, the user's explicit code wins on the wire so
     * callers retain control during version migrations. Verified by inspecting the wire-level
     * [CallData.Error] frame from [HostSerializedChannelImpl.call].
     */
    @Test
    fun explicitCodeOverridesMappedCode() = runBlockingUnit {
        val service = object : TypedErrorService {
            override suspend fun init(input: String): String {
                // InitErrorPayload is bound to code=100 via @KsError. Throwing with
                // code=999 (deliberately outside the binding) should surface 999 on the
                // wire envelope, not 100.
                throw KsrpcException(
                    code = 999,
                    message = "explicit override",
                    data = InitErrorPayload(retry = true, reason = "override")
                )
            }

            override suspend fun plain(input: String): String = error("unused")
            override suspend fun category(input: String): String = error("unused")
        }
        val env = ksrpcEnvironment { }
        val channel = HostSerializedChannelImpl(env)
        try {
            channel.registerDefault(service.serialized(env))
            val inputCallData = env.serialization.createCallData(String.serializer(), "ignored")
            val client = channel.asClient.defaultChannel()
            val response = client.call("init", inputCallData, callId = null)
            assertIs<CallData.Error<String>>(response)
            assertEquals(
                999,
                response.errorCode,
                "Explicit code must override the bound mapping code"
            )
            // Typed payload still rides along on the explicit-code wire — the client-side
            // decoder will fail to decode (no forwardErrorMap entry for code=999) and fall
            // through to RpcException, but the server still emits the encoded payload.
            assertNotNull(response.errorData)
        } finally {
            channel.close()
        }
    }

    /**
     * Unmapped throw (plain runtime exception) falls back to the existing
     * [com.monkopedia.ksrpc.RpcException] envelope — no typed data, generic message routing.
     */
    @Test
    fun unmappedThrowFallsBackToRpcException() = runBlockingUnit {
        val service = object : TypedErrorService {
            override suspend fun init(input: String): String {
                throw IllegalStateException("not mapped")
            }

            override suspend fun plain(input: String): String = error("unused")
            override suspend fun category(input: String): String = error("unused")
        }
        withInProcessChannel(service) { stub ->
            try {
                stub.init("anything")
                fail("Expected RpcException")
            } catch (t: Throwable) {
                assertIs<RpcException>(t)
                assertEquals(KsrpcException.INTERNAL_ERROR_CODE, t.code)
                assertNull(t.data)
            }
        }
    }

    /**
     * KsrpcException with no typed `data` payload: server-side encode declines the
     * typed-payload path (`reverseErrorMap[null::class]` -> miss) and emits a plain
     * [CallData.Error] with the user's explicit code. Client-side decode finds no
     * forwardErrorMap entry, falls through to [RpcException].
     */
    @Test
    fun ksrpcExceptionWithUnboundDataFallsBack() = runBlockingUnit {
        val service = object : TypedErrorService {
            override suspend fun init(input: String): String {
                throw KsrpcException(code = 999, message = "untyped")
            }

            override suspend fun plain(input: String): String = error("unused")
            override suspend fun category(input: String): String = error("unused")
        }
        withInProcessChannel(service) { stub ->
            try {
                stub.init("anything")
                fail("Expected RpcException")
            } catch (t: Throwable) {
                assertIs<RpcException>(t)
                assertEquals(KsrpcException.INTERNAL_ERROR_CODE, t.code)
                assertNull(t.data)
            }
        }
    }

    /**
     * Subclass of a bound base type matches via assignable-class lookup. The @KsError on
     * /category is `type = CategoryError::class` (a sealed base); throwing a
     * [RetryableError] subclass instance should resolve to that binding through
     * `errorMappings.firstOrNull { it.dataType.isInstance(value) }` rather than requiring
     * an exact `dataType == value::class` match. The sealed serializer reconstructs the
     * concrete subclass on the client side via the polymorphic discriminator.
     */
    @Test
    fun subclassThrowMatchesBaseBinding() = runBlockingUnit {
        val service = object : TypedErrorService {
            override suspend fun init(input: String): String = error("unused")
            override suspend fun plain(input: String): String = error("unused")
            override suspend fun category(input: String): String {
                throw KsrpcException(
                    code = 200,
                    message = "transient failure",
                    data = RetryableError(tag = "io", backoffMs = 250L)
                )
            }
        }
        withInProcessChannel(service) { stub ->
            try {
                stub.category("anything")
                fail("Expected KsrpcException")
            } catch (t: Throwable) {
                assertIs<KsrpcException>(t)
                assertEquals(200, t.code)
                val payload = t.data
                // Sealed serializer round-trips the concrete subclass.
                assertIs<RetryableError>(payload)
                assertEquals("io", payload.tag)
                assertEquals(250L, payload.backoffMs)
            }
        }
    }

    /**
     * @KsError-annotated method called with a thrown payload whose runtime class isn't
     * assignable to any binding's dataType — the server emits a [CallData.Error] without
     * typed payload, and the client decodes RpcException without typed data.
     */
    @Test
    fun unboundPayloadClassFallsBack() = runBlockingUnit {
        val service = object : TypedErrorService {
            override suspend fun init(input: String): String {
                // String isn't bound on /init; expect generic fallback.
                throw KsrpcException(
                    code = 100,
                    message = "wrong payload type",
                    data = "not bound"
                )
            }

            override suspend fun plain(input: String): String = error("unused")
            override suspend fun category(input: String): String = error("unused")
        }
        withInProcessChannel(service) { stub ->
            try {
                stub.init("anything")
                fail("Expected RpcException")
            } catch (t: Throwable) {
                assertIs<RpcException>(t)
                assertNull(t.data)
            }
        }
    }

    /**
     * Endpoint-not-found frames still route through [RpcEndpointException] —
     * typed-error routing must not capture them. The wire-level [CallData.Error] carries the
     * sentinel [KsrpcException.ENDPOINT_NOT_FOUND_CODE] code.
     */
    @Test
    fun endpointNotFoundIsUnaffected() = runBlockingUnit {
        val service = object : TypedErrorService {
            override suspend fun init(input: String): String = "ok"
            override suspend fun plain(input: String): String = "ok"
            override suspend fun category(input: String): String = "ok"
        }
        val env = ksrpcEnvironment { }
        val channel = HostSerializedChannelImpl(env)
        try {
            channel.registerDefault(service.serialized(env))
            val client = channel.asClient.defaultChannel()
            val response = client.call("missing", CallData.create("ignored"), callId = null)
            assertIs<CallData.Error<String>>(response)
            assertEquals(KsrpcException.ENDPOINT_NOT_FOUND_CODE, response.errorCode)
        } finally {
            channel.close()
        }
    }

    /**
     * Unknown wire `code` (sender mapped a code the receiver doesn't know about, e.g.
     * version skew where the receiver's @KsError set is a subset): client falls back to
     * RpcException so the call still surfaces an error, just without typed data.
     *
     * Exercised by handing a [CallData.Error] directly to [RpcMethod.decodeError] without
     * any forwardErrorMap entry for the code.
     */
    @Test
    fun unknownCodeFallsBack() {
        val env = ksrpcEnvironment { }
        val rpcObject = rpcObject<TypedErrorService>()
        val initMethod = rpcObject.findEndpoint("init")
        val unknownError = CallData.Error<String>(
            errorCode = 4242,
            errorMessage = "unknown error",
            errorData = null
        )
        val throwable = initMethod.decodeError(unknownError, env)
        assertIs<RpcException>(throwable)
        assertEquals("unknown error", throwable.message)
    }

    /**
     * Unknown wire `code` with a payload that doesn't match any binding falls through to
     * RpcException — proves [RpcMethod.decodeError] doesn't trip on unrecognised payloads.
     */
    @Test
    fun unknownCodeWithPayloadFallsBack() {
        val env = ksrpcEnvironment { }
        val rpcObject = rpcObject<TypedErrorService>()
        val initMethod = rpcObject.findEndpoint("init")
        val unknownError = CallData.Error<String>(
            errorCode = 4243,
            errorMessage = "stale code",
            errorData = "{\"some\":\"data\"}"
        )
        val throwable = initMethod.decodeError(unknownError, env)
        assertIs<RpcException>(throwable)
        assertEquals("stale code", throwable.message)
    }

    private suspend fun withInProcessChannel(
        service: TypedErrorService,
        body: suspend (TypedErrorService) -> Unit
    ) {
        val env = ksrpcEnvironment { }
        val channel = HostSerializedChannelImpl(env)
        try {
            channel.registerDefault(service.serialized(env))
            val stub = channel.asClient.defaultChannel().toStub<TypedErrorService, String>()
            body(stub)
        } finally {
            try {
                channel.close()
            } catch (_: Throwable) {
            }
        }
    }
}

/**
 * Round-trip pipe-transport coverage for the typed-error path: exercises the full
 * encode / wire / decode flow over a real socket pipe (instead of the in-process channel
 * above) so we verify the bytes survive a round-trip serialization, not just the in-memory
 * CallData hand-off. The packet wire grew optional `errorCode` / `errorMessage` fields — see
 * [com.monkopedia.ksrpc.packets.internal.Packet].
 */
class KsErrorRoutingPipeTest :
    RpcFunctionalityTest(
        supportedTypes = listOf(
            TestType.PIPE,
            TestType.SERIALIZE,
            TestType.HTTP,
            TestType.WEBSOCKET
        ),
        serializedChannel = {
            val service = object : TypedErrorService {
                override suspend fun init(input: String): String {
                    throw KsrpcException(
                        code = 100,
                        message = "pipe failure",
                        data = InitErrorPayload(retry = false, reason = "from-pipe")
                    )
                }

                override suspend fun plain(input: String): String = "ok"
                override suspend fun category(input: String): String = "ok"
            }
            service.serialized(ksrpcEnvironment { })
        },
        verifyOnChannel = { channel ->
            val stub = channel.toStub<TypedErrorService, String>()
            try {
                stub.init("ignored")
                fail("Expected KsrpcException")
            } catch (t: Throwable) {
                assertIs<KsrpcException>(t)
                assertEquals(100, t.code)
                val payload = t.data
                assertIs<InitErrorPayload>(payload)
                assertEquals(false, payload.retry)
                assertEquals("from-pipe", payload.reason)
            }
        }
    )

/**
 * Verify that unmapped throws over a real pipe transport still surface as RpcException —
 * back-compat with pre-#78 behavior across the wire.
 */
class KsErrorRoutingPipeFallbackTest :
    RpcFunctionalityTest(
        supportedTypes = listOf(
            TestType.PIPE,
            TestType.SERIALIZE,
            TestType.HTTP,
            TestType.WEBSOCKET
        ),
        serializedChannel = {
            val service = object : TypedErrorService {
                override suspend fun init(input: String): String {
                    throw IllegalArgumentException("not bound")
                }

                override suspend fun plain(input: String): String = "ok"
                override suspend fun category(input: String): String = "ok"
            }
            service.serialized(ksrpcEnvironment { })
        },
        verifyOnChannel = { channel ->
            val stub = channel.toStub<TypedErrorService, String>()
            try {
                stub.init("ignored")
                fail("Expected RpcException")
            } catch (t: Throwable) {
                assertIs<RpcException>(t)
                assertEquals(KsrpcException.INTERNAL_ERROR_CODE, t.code)
                assertNull(t.data)
            }
        }
    )
