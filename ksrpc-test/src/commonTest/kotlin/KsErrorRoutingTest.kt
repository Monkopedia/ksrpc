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
import kotlin.test.assertNull
import kotlin.test.fail
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Tests for #78 — runtime error routing for `@KsError` mappings. Under the typed-Throwable
 * contract the bound type IS the thrown class: server-side handlers `throw MyTypedError(...)`
 * directly (no `KsrpcException` wrapper). The server-side encode path lives on
 * [RpcMethod.encodeError]: it resolves the throwable's runtime class to the @KsError-bound
 * code via [RpcMethod.reverseErrorMap] and embeds the encoded throwable inside a
 * [CallData.Error] frame whose `errorData` is the wire-format-encoded payload. The
 * client-side decode path lives on [RpcMethod.decodeError] (called from
 * [RpcMethod.callChannel] on receive): it consults [RpcMethod.forwardErrorMap], deserializes
 * the typed Throwable, and re-throws — callers `catch (e: MyError)` typed.
 *
 * The variant ([CallData.Error]) is the discriminator on the wire — there is no
 * `isError(callData)` round-trip through the serializer anymore, and transports natively
 * encode/decode the error variant rather than smuggling errors through `Serialized`.
 */

@Serializable
class InitError(val retry: Boolean, val reason: String) : RuntimeException() {
    override val message: String get() = "init failed: $reason"
}

@Serializable
class VersionError(val expected: Int, val actual: Int) : RuntimeException() {
    override val message: String get() = "version mismatch: expected=$expected actual=$actual"
}

@Serializable
sealed class CategoryError : RuntimeException() {
    abstract val tag: String
}

@Serializable
@SerialName("retry")
class RetryableError(override val tag: String, val backoffMs: Long) : CategoryError() {
    override val message: String get() = "retryable: tag=$tag backoffMs=$backoffMs"
}

@Serializable
@SerialName("permanent")
class PermanentError(override val tag: String) : CategoryError() {
    override val message: String get() = "permanent: tag=$tag"
}

@KsService
interface TypedErrorService : RpcService {
    @KsMethod("/init")
    @KsError(code = 100, type = InitError::class)
    @KsError(code = 101, type = VersionError::class)
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
     * Server throws a typed Throwable bound via @KsError; client receives the same typed
     * Throwable directly (the bound class IS the wire payload's class).
     */
    @Test
    fun mappedThrowDeliversTypedDataToClient() = runBlockingUnit {
        val service = object : TypedErrorService {
            override suspend fun init(input: String): String {
                throw InitError(retry = true, reason = "bad input: $input")
            }

            override suspend fun plain(input: String): String = error("unused")
            override suspend fun category(input: String): String = error("unused")
        }
        withInProcessChannel(service) { stub ->
            try {
                stub.init("payload")
                fail("Expected InitError")
            } catch (t: Throwable) {
                // The throw is rebuilt on the client by deserializing the typed payload;
                // identity is not round-tripped but the bound class is.
                assertIs<InitError>(t)
                assertEquals(true, t.retry)
                assertEquals("bad input: payload", t.reason)
            }
        }
    }

    /**
     * Multiple @KsError bindings on a single method: each typed throw resolves to its own
     * code + deserialized typed Throwable independently.
     */
    @Test
    fun secondMappingResolvesIndependently() = runBlockingUnit {
        val service = object : TypedErrorService {
            override suspend fun init(input: String): String {
                throw VersionError(expected = 7, actual = 3)
            }

            override suspend fun plain(input: String): String = error("unused")
            override suspend fun category(input: String): String = error("unused")
        }
        withInProcessChannel(service) { stub ->
            try {
                stub.init("anything")
                fail("Expected VersionError")
            } catch (t: Throwable) {
                assertIs<VersionError>(t)
                assertEquals(7, t.expected)
                assertEquals(3, t.actual)
            }
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
     * KsrpcException with no typed `data` payload: server-side encode finds no binding for
     * the thrown class and emits a plain [CallData.Error] with the user's explicit code.
     * Client-side decode finds no forwardErrorMap entry; since the code (999) is neither
     * sentinel the forward-compat path surfaces a generic [KsrpcException] with the raw
     * (here null) errorData attached.
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
                fail("Expected KsrpcException")
            } catch (t: Throwable) {
                assertIs<KsrpcException>(t)
                assertEquals(999, t.code)
                assertNull(t.data)
            }
        }
    }

    /**
     * Subclass of a bound base type matches via assignable-class lookup. The @KsError on
     * /category is `type = CategoryError::class` (a sealed base); throwing a
     * [RetryableError] subclass instance should resolve to that binding through
     * `errorMappings.firstOrNull { it.dataType.isInstance(t) }` rather than requiring an
     * exact `dataType == t::class` match. The sealed serializer reconstructs the concrete
     * subclass on the client side via the polymorphic discriminator.
     */
    @Test
    fun subclassThrowMatchesBaseBinding() = runBlockingUnit {
        val service = object : TypedErrorService {
            override suspend fun init(input: String): String = error("unused")
            override suspend fun plain(input: String): String = error("unused")
            override suspend fun category(input: String): String {
                throw RetryableError(tag = "io", backoffMs = 250L)
            }
        }
        withInProcessChannel(service) { stub ->
            try {
                stub.category("anything")
                fail("Expected RetryableError")
            } catch (t: Throwable) {
                // Sealed serializer round-trips the concrete subclass.
                assertIs<RetryableError>(t)
                assertEquals("io", t.tag)
                assertEquals(250L, t.backoffMs)
            }
        }
    }

    /**
     * @KsError-annotated method called with a thrown Throwable whose runtime class isn't
     * assignable to any binding's dataType — the server emits a [CallData.Error] without
     * typed payload and the client decodes [RpcException] (INTERNAL_ERROR_CODE) without
     * typed data.
     */
    @Test
    fun unboundThrowableFallsBack() = runBlockingUnit {
        val service = object : TypedErrorService {
            override suspend fun init(input: String): String {
                // IllegalArgumentException isn't bound on /init; expect generic fallback.
                throw IllegalArgumentException("not bound")
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
     * Forward-compat: an unknown wire `code` (e.g. a newer server's typed error not bound
     * on this client) surfaces a generic [KsrpcException] carrying the raw wire-format
     * payload bytes as [KsrpcException.data], so callers can still inspect the data even
     * without the @KsError binding. Exercised by handing a [CallData.Error] directly to
     * [RpcMethod.decodeError] without any forwardErrorMap entry for the code.
     */
    @Test
    fun unknownWireCodeSurfacesRawData() {
        val env = ksrpcEnvironment { }
        val rpcObject = rpcObject<TypedErrorService>()
        val initMethod = rpcObject.findEndpoint("init")
        val unknownError = CallData.Error<String>(
            errorCode = 4242,
            errorMessage = "unknown error",
            errorData = "{\"some\":\"json\"}"
        )
        val throwable = initMethod.decodeError(unknownError, env)
        assertIs<KsrpcException>(throwable)
        assertEquals(4242, throwable.code)
        assertEquals("unknown error", throwable.message)
        assertEquals("{\"some\":\"json\"}", throwable.data)
    }

    /**
     * Forward-compat: an unknown wire `code` with no payload still surfaces a generic
     * [KsrpcException] (with `data == null`) so the call still raises an error and callers
     * see the originating code.
     */
    @Test
    fun unknownWireCodeWithoutPayloadSurfacesKsrpcException() {
        val env = ksrpcEnvironment { }
        val rpcObject = rpcObject<TypedErrorService>()
        val initMethod = rpcObject.findEndpoint("init")
        val unknownError = CallData.Error<String>(
            errorCode = 4243,
            errorMessage = "stale code",
            errorData = null
        )
        val throwable = initMethod.decodeError(unknownError, env)
        assertIs<KsrpcException>(throwable)
        assertEquals(4243, throwable.code)
        assertEquals("stale code", throwable.message)
        assertNull(throwable.data)
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
                    throw InitError(retry = false, reason = "from-pipe")
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
                fail("Expected InitError")
            } catch (t: Throwable) {
                assertIs<InitError>(t)
                assertEquals(false, t.retry)
                assertEquals("from-pipe", t.reason)
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
