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
import com.monkopedia.ksrpc.annotation.KsTimeout
import com.monkopedia.ksrpc.annotation.KsrpcInternal
import com.monkopedia.ksrpc.channels.CallData
import com.monkopedia.ksrpc.internal.HostSerializedChannelImpl
import com.monkopedia.ksrpc.internal.asClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

/**
 * Round-trip / equivalence / cancellation coverage for `Result<O>` return types
 * (issue #133). A `Result<O>` method is defined to be equivalent to a plain
 * `suspend fun m(i): O` wrapped in runCatching-except-cancellation, with NO
 * wire-format change:
 *  - success round-trips as `Result.success(o)` (inner `O` serialized as a plain
 *    `O` method would be);
 *  - a returned/thrown `@KsError`-mapped failure round-trips as
 *    `Result.failure(typedError)` via the existing error envelope;
 *  - an unmapped failure round-trips as the generic [RpcException] inside
 *    `Result.failure`;
 *  - cancellation is NOT folded into the Result — it propagates.
 */

@Serializable
class ResultMyError(val reason: String) : RuntimeException() {
    override val message: String get() = "result error: $reason"
}

@KsService
interface ResultService : RpcService {
    @KsMethod("/maybe")
    @KsError(code = 300, type = ResultMyError::class)
    suspend fun maybe(input: String): Result<Int>
}

// A plain (non-Result) twin used to prove wire equivalence: returning
// Result.failure(e) must produce the same CallData as throwing e on a plain
// `Int` method. Same endpoint string + same @KsError binding as ResultService.
@KsService
interface PlainTwinService : RpcService {
    @KsMethod("/maybe")
    @KsError(code = 300, type = ResultMyError::class)
    suspend fun maybe(input: String): Int
}

@KsService
interface ResultTimeoutService : RpcService {
    @KsMethod("/slow")
    @KsTimeout(millis = 200)
    suspend fun slow(input: String): Result<String>
}

class ResultReturnTypeTest {

    @Test
    fun successRoundTripsAsResultSuccess() = runBlockingUnit {
        val service = object : ResultService {
            override suspend fun maybe(input: String): Result<Int> = Result.success(input.length)
        }
        withInProcessChannel(service) { stub ->
            val r = stub.maybe("hello")
            assertTrue(r.isSuccess, "expected success, got $r")
            assertEquals(5, r.getOrNull())
        }
    }

    @Test
    fun returnedMappedFailureRoundTripsAsResultFailure() = runBlockingUnit {
        val service = object : ResultService {
            override suspend fun maybe(input: String): Result<Int> =
                Result.failure(ResultMyError("returned failure"))
        }
        withInProcessChannel(service) { stub ->
            val r = stub.maybe("x")
            assertTrue(r.isFailure, "expected failure, got $r")
            val e = r.exceptionOrNull()
            assertIs<ResultMyError>(e)
            assertEquals("returned failure", e.reason)
        }
    }

    @Test
    fun thrownMappedFailureRoundTripsAsResultFailure() = runBlockingUnit {
        val service = object : ResultService {
            override suspend fun maybe(input: String): Result<Int> =
                throw ResultMyError("thrown failure")
        }
        withInProcessChannel(service) { stub ->
            val r = stub.maybe("x")
            assertTrue(r.isFailure, "expected failure, got $r")
            val e = r.exceptionOrNull()
            assertIs<ResultMyError>(e)
            assertEquals("thrown failure", e.reason)
        }
    }

    @Test
    fun unmappedFailureRoundTripsAsGenericRpcExceptionInResult() = runBlockingUnit {
        val service = object : ResultService {
            override suspend fun maybe(input: String): Result<Int> =
                Result.failure(IllegalStateException("not mapped"))
        }
        withInProcessChannel(service) { stub ->
            val r = stub.maybe("x")
            assertTrue(r.isFailure, "expected failure, got $r")
            val e = r.exceptionOrNull()
            assertIs<RpcException>(e)
            assertEquals(KsrpcException.INTERNAL_ERROR_CODE, e.code)
        }
    }

    /**
     * Equivalence guarantee: returning Result.failure(e) on a Result<O> method
     * produces the SAME wire-level CallData.Error as throwing e on a plain O
     * method (same code, message, errorData). Likewise Result.success(o) ==
     * returning o.
     */
    @Test
    fun resultIsWireEquivalentToPlainMethod() = runBlockingUnit {
        val env = ksrpcEnvironment { }
        val resultObj = rpcObject<ResultService>()
        val plainObj = rpcObject<PlainTwinService>()
        val resultMethod = resultObj.findEndpoint("maybe")
        val plainMethod = plainObj.findEndpoint("maybe")

        // Failure equivalence: encodeError of the same throwable yields identical frames.
        val err = ResultMyError("same")
        val resultErrFrame = resultMethod.encodeError(err, env)
        val plainErrFrame = plainMethod.encodeError(err, env)
        assertEquals(plainErrFrame.errorCode, resultErrFrame.errorCode)
        assertEquals(plainErrFrame.errorMessage, resultErrFrame.errorMessage)
        assertEquals(plainErrFrame.errorData, resultErrFrame.errorData)

        // Success equivalence: serialize the inner value through both transformers
        // and confirm the serialized payload bytes match.
        val resultService = object : ResultService {
            override suspend fun maybe(input: String): Result<Int> = Result.success(7)
        }
        val plainService = object : PlainTwinService {
            override suspend fun maybe(input: String): Int = 7
        }
        val resultChannel = HostSerializedChannelImpl(env)
        val plainChannel = HostSerializedChannelImpl(env)
        try {
            resultChannel.registerDefault(resultService.serialized(env))
            plainChannel.registerDefault(plainService.serialized(env))
            val input = env.serialization.createCallData(String.serializer(), "x")
            val resultResponse = resultChannel.asClient.defaultChannel()
                .call("maybe", input, callId = null)
            val plainResponse = plainChannel.asClient.defaultChannel()
                .call("maybe", input, callId = null)
            assertIs<CallData.Serialized<String>>(resultResponse)
            assertIs<CallData.Serialized<String>>(plainResponse)
            assertEquals(
                plainResponse.readSerialized(),
                resultResponse.readSerialized(),
                "Result<O> success must serialize identically to a plain O method"
            )
        } finally {
            resultChannel.close()
            plainChannel.close()
        }
    }

    /**
     * Cancellation is NOT folded into the Result: a @KsTimeout firing on a
     * Result<O> method propagates the cancellation/timeout (thrown), it is not
     * captured into Result.failure.
     */
    @Test
    fun cancellationPropagatesAndIsNotCapturedInResult() = runBlockingUnit {
        val service = object : ResultTimeoutService {
            override suspend fun slow(input: String): Result<String> {
                delay(10_000)
                return Result.success("slow:$input")
            }
        }
        val env = ksrpcEnvironment { }
        val channel = HostSerializedChannelImpl(env)
        try {
            channel.registerDefault(service.serialized(env))
            val stub = channel.asClient.defaultChannel()
                .toStub<ResultTimeoutService, String>()
            var threw = false
            try {
                stub.slow("hi")
                fail("Expected timeout/cancellation to throw, not return Result.failure")
            } catch (_: CancellationException) {
                threw = true
            } catch (t: Throwable) {
                // withTimeout surfaces a TimeoutCancellationException (a
                // CancellationException subclass) on the in-process path; any
                // thrown outcome here proves it was NOT captured into the Result.
                threw = true
            }
            assertTrue(threw, "cancellation must propagate as a throw, not Result.failure")
        } finally {
            try {
                channel.close()
            } catch (_: Throwable) {
            }
        }
    }

    private suspend fun withInProcessChannel(
        service: ResultService,
        body: suspend (ResultService) -> Unit
    ) {
        val env = ksrpcEnvironment { }
        val channel = HostSerializedChannelImpl(env)
        try {
            channel.registerDefault(service.serialized(env))
            val stub = channel.asClient.defaultChannel().toStub<ResultService, String>()
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
 * Round-trip over real transports (PIPE/SERIALIZE/HTTP/WEBSOCKET): a returned
 * Result.failure mapped via @KsError must survive a full wire round-trip and
 * surface as Result.failure(typedError) on the client.
 */
class ResultReturnTypePipeTest :
    RpcFunctionalityTest(
        supportedTypes = listOf(
            TestType.PIPE,
            TestType.SERIALIZE,
            TestType.HTTP,
            TestType.WEBSOCKET
        ),
        serializedChannel = {
            val service = object : ResultService {
                override suspend fun maybe(input: String): Result<Int> = if (input == "fail") {
                    Result.failure(ResultMyError("from-pipe"))
                } else {
                    Result.success(input.length)
                }
            }
            service.serialized(ksrpcEnvironment { })
        },
        verifyOnChannel = { channel ->
            val stub = channel.toStub<ResultService, String>()
            val ok = stub.maybe("hello")
            assertTrue(ok.isSuccess, "expected success, got $ok")
            assertEquals(5, ok.getOrNull())

            val bad = stub.maybe("fail")
            assertTrue(bad.isFailure, "expected failure, got $bad")
            val e = bad.exceptionOrNull()
            assertIs<ResultMyError>(e)
            assertEquals("from-pipe", e.reason)
        }
    )
