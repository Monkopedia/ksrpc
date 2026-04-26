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
 * round-trips successful payloads. This test pins the encode/decode contract end-to-end so
 * any future tweak to the JNI envelope can't silently drop the typed-error round-trip.
 *
 * Lives in `ksrpc-test/jvmTest` because [JniSerialization] eagerly initializes the native
 * bindings and that requires the test native lib (built by this module) to be loaded first.
 */
class JniTypedErrorRoutingTest {

    @Serializable
    data class JniErrorPayload(val retry: Boolean, val reason: String)

    @KsService
    interface JniTypedService : RpcService {
        @KsMethod("/init")
        @KsError(code = 100, type = JniErrorPayload::class)
        suspend fun init(input: String): String
    }

    @Test
    fun typedErrorRoundTripsThroughJniEnvelope() {
        loadNativeLib()
        val env = jniEnv()
        val rpcObject = rpcObject<JniTypedService>()
        val initMethod = rpcObject.findEndpoint("init")
        val payload = JniErrorPayload(retry = true, reason = "bad input")

        // Server-side encode: thrown KsrpcException with bound payload becomes a
        // CallData.Error<JniSerialized> whose errorData is the payload encoded via
        // JniSerialization.
        val encoded = initMethod.encodeError(
            KsrpcException(code = 100, message = "init failed", data = payload),
            env
        )
        assertIs<CallData.Error<JniSerialized>>(encoded)
        assertEquals(100, encoded.errorCode)
        // [Throwable.asString] on JVM is the full stack — match contains rather than equals.
        kotlin.test.assertTrue(
            encoded.errorMessage.contains("init failed"),
            encoded.errorMessage
        )
        assertNotNull(encoded.errorData, "Typed payload must survive JNI encode")

        // Client-side decode: CallData.Error -> KsrpcException with deserialized typed data.
        val decoded = initMethod.decodeError(encoded, env)
        assertIs<KsrpcException>(decoded)
        assertEquals(100, decoded.code)
        kotlin.test.assertTrue(decoded.message.contains("init failed"), decoded.message)
        val data = decoded.data
        assertIs<JniErrorPayload>(data)
        assertEquals(payload, data)
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

    private fun jniEnv(): KsrpcEnvironment<JniSerialized> =
        ksrpcEnvironment(JniSerialization()) { }

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
