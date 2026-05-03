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
import com.monkopedia.ksrpc.jni.JniSerialization
import com.monkopedia.ksrpc.jni.JniSerialized
import com.monkopedia.ksrpc.jni.NativeUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.serialization.Serializable

/**
 * Round-trip coverage for the JNI [JniSerialization] + [RpcMethod] error path with a typed
 * `@KsError` payload attached. The JNI envelope's flat-list shape no longer cares about
 * "is error" — error frames are carried as [CallData.Error] by the routing layer
 * ([RpcMethod.encodeError] / [RpcMethod.decodeError]) and the JNI [JniSerialization] only
 * round-trips successful payloads. Under the typed-Throwable contract the bound type IS
 * the thrown class, so the encode side is fed a `JniError` directly (not a wrapper) and
 * the decode side reconstitutes the same typed Throwable.
 *
 * Lives in `ksrpc-test/jvmTest` because [JniSerialization] eagerly initializes the native
 * bindings and that requires the test native lib (built by this module) to be loaded first.
 */
class JniTypedErrorRoutingTest {

    @Serializable
    class JniError(val retry: Boolean, val reason: String) : RuntimeException() {
        override val message: String get() = "init failed: $reason"
    }

    @KsService
    interface JniTypedService : RpcService {
        @KsMethod("/init")
        @KsError(code = 100, type = JniError::class)
        suspend fun init(input: String): String
    }

    @Test
    fun typedErrorRoundTripsThroughJniEnvelope() {
        loadNativeLib()
        val env = jniEnv()
        val rpcObject = rpcObject<JniTypedService>()
        val initMethod = rpcObject.findEndpoint("init")
        val thrown = JniError(retry = true, reason = "bad input")

        // Server-side encode: thrown JniError becomes a CallData.Error<JniSerialized>
        // whose errorData is the throwable encoded via JniSerialization. The wire `code`
        // is sourced from the @KsError binding, not from any wrapper.
        val encoded = initMethod.encodeError(thrown, env)
        assertIs<CallData.Error<JniSerialized>>(encoded)
        assertEquals(100, encoded.errorCode)
        assertEquals("init failed: bad input", encoded.errorMessage)
        assertNotNull(encoded.errorData, "Typed payload must survive JNI encode")

        // Client-side decode: CallData.Error -> typed JniError Throwable.
        val decoded = initMethod.decodeError(encoded, env)
        assertIs<JniError>(decoded)
        assertEquals(true, decoded.retry)
        assertEquals("bad input", decoded.reason)
        assertEquals("init failed: bad input", decoded.message)
    }

    @Test
    fun untypedErrorOmitsPayloadSlot() {
        loadNativeLib()
        val env = jniEnv()
        val rpcObject = rpcObject<JniTypedService>()
        val initMethod = rpcObject.findEndpoint("init")

        // Plain Throwable (no @KsError binding) -> CallData.Error with no errorData.
        val encoded = initMethod.encodeError(IllegalStateException("boom"), env)
        assertIs<CallData.Error<JniSerialized>>(encoded)
        assertEquals(KsrpcException.INTERNAL_ERROR_CODE, encoded.errorCode)
        assertNull(encoded.errorData, "No payload binding -> no errorData on the wire")

        val decoded = initMethod.decodeError(encoded, env)
        assertIs<RpcException>(decoded)
    }

    @Test
    fun endpointNotFoundIsDistinguishable() {
        loadNativeLib()
        val env = jniEnv()
        val rpcObject = rpcObject<JniTypedService>()
        val initMethod = rpcObject.findEndpoint("init")

        val encoded = initMethod.encodeError(RpcEndpointException("missing endpoint"), env)
        assertIs<CallData.Error<JniSerialized>>(encoded)
        assertEquals(KsrpcException.ENDPOINT_NOT_FOUND_CODE, encoded.errorCode)
        assertNull(encoded.errorData)

        val decoded = initMethod.decodeError(encoded, env)
        assertIs<RpcEndpointException>(decoded)
    }

    private fun jniEnv(): KsrpcEnvironment<JniSerialized> = ksrpcEnvironment(JniSerialization()) { }

    private fun loadNativeLib() {
        NativeUtils.loadLibraryFromJar("/libs/libksrpc_test.${extension()}")
    }

    private fun extension(): String =
        if (NativeUtils::class.java.getResourceAsStream("/libs/libksrpc_test.so") != null) {
            "so"
        } else {
            "dylib"
        }
}
