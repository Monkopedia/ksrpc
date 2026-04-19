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
import com.monkopedia.ksrpc.annotation.KsTimeout
import com.monkopedia.ksrpc.MetadataValue
import com.monkopedia.ksrpc.internal.HostSerializedChannelImpl
import com.monkopedia.ksrpc.internal.asClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay

@KsService
interface TimeoutTestService : RpcService {
    @KsMethod("/fast")
    @KsTimeout(seconds = 5)
    suspend fun fast(input: String): String

    @KsMethod("/slow")
    @KsTimeout(millis = 200)
    suspend fun slow(input: String): String

    @KsMethod("/no_timeout")
    suspend fun noTimeout(input: String): String

    @KsMethod("/combined")
    @KsTimeout(seconds = 1, millis = 500)
    suspend fun combined(input: String): String

    @KsMethod("/minutes")
    @KsTimeout(minutes = 2)
    suspend fun withMinutes(input: String): String
}

class TimeoutTestServiceImpl : TimeoutTestService {
    override suspend fun fast(input: String): String = "fast:$input"

    override suspend fun slow(input: String): String {
        delay(2000)
        return "slow:$input"
    }

    override suspend fun noTimeout(input: String): String = "noTimeout:$input"

    override suspend fun combined(input: String): String = "combined:$input"

    override suspend fun withMinutes(input: String): String = "minutes:$input"
}

class KsTimeoutMetadataTest {
    private val rpcObject = rpcObject<TimeoutTestService>()

    @Test
    fun timeoutMillisIsCapturedFromAnnotation() {
        val method = rpcObject.findEndpoint("fast")
        val timeout = method.timeoutMillis
        assertNotNull(timeout)
        assertEquals(5000L, timeout)
    }

    @Test
    fun methodWithoutTimeoutHasNullTimeoutMillis() {
        val method = rpcObject.findEndpoint("no_timeout")
        assertNull(method.timeoutMillis)
    }

    @Test
    fun timeoutMetadataHasCorrectFqName() {
        val method = rpcObject.findEndpoint("fast")
        val meta = method.metadata("com.monkopedia.ksrpc.annotation.KsTimeout")
        assertNotNull(meta)
        assertEquals("com.monkopedia.ksrpc.annotation.KsTimeout", meta.annotationFqName)
    }

    @Test
    fun shortTimeoutValueIsCaptured() {
        val method = rpcObject.findEndpoint("slow")
        val timeout = method.timeoutMillis
        assertNotNull(timeout)
        assertEquals(200L, timeout)
    }

    @Test
    fun combinedSecondsAndMillisAreSummed() {
        val method = rpcObject.findEndpoint("combined")
        val timeout = method.timeoutMillis
        assertNotNull(timeout)
        assertEquals(1500L, timeout)
    }

    @Test
    fun minutesTimeoutIsConvertedToMillis() {
        val method = rpcObject.findEndpoint("minutes")
        val timeout = method.timeoutMillis
        assertNotNull(timeout)
        assertEquals(120_000L, timeout)
    }

    @Test
    fun combinedMetadataHasAllParams() {
        val method = rpcObject.findEndpoint("combined")
        val meta = method.metadata("com.monkopedia.ksrpc.annotation.KsTimeout")
        assertNotNull(meta)
        assertEquals(
            500L,
            (meta.argument("millis") as? MetadataValue.LongValue)?.value
        )
        assertEquals(
            1L,
            (meta.argument("seconds") as? MetadataValue.LongValue)?.value
        )
    }
}

class KsTimeoutRuntimeTest {
    @Test
    fun callWithinTimeoutSucceeds() = runBlockingUnit {
        val channel = HostSerializedChannelImpl(createEnv())
        try {
            val service = TimeoutTestServiceImpl()
            val serialized = service.serialized(
                rpcObject<TimeoutTestService>(),
                channel.env
            )
            channel.registerDefault(serialized)
            val stub = rpcObject<TimeoutTestService>()
                .createStub(channel.asClient.defaultChannel())
            val result = stub.fast("hello")
            assertEquals("fast:hello", result)
        } finally {
            channel.close()
        }
    }

    @Test
    fun callExceedingTimeoutFails() = runBlockingUnit {
        val channel = HostSerializedChannelImpl(createEnv())
        try {
            val service = TimeoutTestServiceImpl()
            val serialized = service.serialized(
                rpcObject<TimeoutTestService>(),
                channel.env
            )
            channel.registerDefault(serialized)
            val stub = rpcObject<TimeoutTestService>()
                .createStub(channel.asClient.defaultChannel())
            // In-process transports may wrap the TimeoutCancellationException into
            // an RpcException via the error-encoding path, so we assert on the
            // general Exception type. On real transports the original
            // TimeoutCancellationException propagates directly.
            assertFailsWith<Exception> {
                stub.slow("hello")
            }
        } finally {
            channel.close()
        }
    }

    @Test
    fun callWithoutTimeoutAnnotationDoesNotTimeout() = runBlockingUnit {
        val channel = HostSerializedChannelImpl(createEnv())
        try {
            val service = TimeoutTestServiceImpl()
            val serialized = service.serialized(
                rpcObject<TimeoutTestService>(),
                channel.env
            )
            channel.registerDefault(serialized)
            val stub = rpcObject<TimeoutTestService>()
                .createStub(channel.asClient.defaultChannel())
            val result = stub.noTimeout("hello")
            assertEquals("noTimeout:hello", result)
        } finally {
            channel.close()
        }
    }

    private fun createEnv() = ksrpcEnvironment { }
}
