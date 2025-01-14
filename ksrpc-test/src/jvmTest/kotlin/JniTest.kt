/**
 * Copyright (C) 2025 Jason Monk <monkopedia@gmail.com>
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

import ComplexClass
import OtherClass
import com.monkopedia.ksrpc.jni.JavaJniContinuation
import com.monkopedia.ksrpc.jni.JniConnection
import com.monkopedia.ksrpc.jni.JniSer
import com.monkopedia.ksrpc.jni.JniSerialization
import com.monkopedia.ksrpc.jni.JniSerialized
import com.monkopedia.ksrpc.jni.NativeUtils
import com.monkopedia.ksrpc.jni.asContinuation
import com.monkopedia.ksrpc.jni.decodeFromJni
import com.monkopedia.ksrpc.jni.encodeToJni
import com.monkopedia.ksrpc.jni.newTypeConverter
import com.monkopedia.ksrpc.jni.withConverter
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.readText
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class JniTest {

    @Test
    fun testSerialization() {
        NativeUtils.loadLibraryFromJar("/libs/libksrpc_test.${extension()}")
        val encode = ComplexClass(
            5,
            listOf("test", "next", null, "bolu"),
            ComplexClass(null, null, null, mapOf("First" to OtherClass(true, .5f))),
            mapOf("second" to OtherClass(false, 1.67f))
        )
        val result = JniSer.encodeToJni(encode)
        val decoded = JniSer.decodeFromJni<ComplexClass>(result)
        assertEquals(decoded, encode)
        val nativeVersion = NativeHost().serializeDeserialize(result)
        val nativeDecoded = JniSer.decodeFromJni<ComplexClass>(nativeVersion)
        assertEquals(nativeDecoded, encode.copy(intValue = 6))
    }

    @Test
    fun testContinuations() = runBlockingUnit {
        NativeUtils.loadLibraryFromJar("/libs/libksrpc_test.${extension()}")
        newTypeConverter<Any>()
        val results = List(2) { CompletableDeferred<String>() }
        val rec = results.toMutableList()
        val receiver = object : Receiver {
            override fun message(str: String) {
                rec.removeFirst().complete(str)
            }
        }
        val continuations = buildList {
            NativeHost().createContinuations(receiver, this)
        }.map {
            it.asContinuation(newTypeConverter<Any>().int)
        }
        continuations[0].resume(2)
        continuations[1].resume(3)
        continuations[2].resume(4)
        assertEquals("Result: 2", results[0].await())
        assertEquals("3 + 4 = 7", results[1].await())
    }

    @Test
    fun testJvmContinuations() = runBlockingUnit {
        NativeUtils.loadLibraryFromJar("/libs/libksrpc_test.${extension()}")
        newTypeConverter<Any>()
        val result1 = CompletableDeferred<Int>()
        val result2 = CompletableDeferred<Int>()
        val continuation1 = CompletableDeferred<JavaJniContinuation<Int>>()
        val continuation2 = CompletableDeferred<JavaJniContinuation<Int>>()
        launch {
            val result = suspendCoroutine<Int> {
                continuation2.complete(it.withConverter(newTypeConverter<Any>().int))
            }
            result2.complete(result)
        }
        val relay2 = NativeHost().createContinuationRelay(continuation2.await())
        launch {
            val result = suspendCoroutine<Int> {
                continuation1.complete(it.withConverter(newTypeConverter<Any>().int))
            }
            result1.complete(result)
            relay2.asContinuation(newTypeConverter<Any>().int).resume(result * 2)
        }
        val relay1 = NativeHost().createContinuationRelay(continuation1.await())
        relay1.asContinuation(newTypeConverter<Any>().int).resume(1)
        relay2.asContinuation(newTypeConverter<Any>().int).resume(4)
        assertEquals(2, result1.await())
        assertEquals(5, result2.await())
    }

    @Test
    fun testConnectionPing() = runBlockingUnit {
        val service = createService()
        val result = service.ping("ping")
        assertEquals("pong", result)
    }

    @Test
    fun testBinaryTest() = runBlockingUnit {
        val stub = createService()
        val response = stub.binaryRpc("Hello" to "world")
        val str = response.readRemaining().readText()
        assertEquals("Hello world", str)
        assertEquals("pong", stub.ping("ping"))
    }

    @Test
    fun testMultiFrameBinaryTest() = runBlockingUnit {
        val stub = createService()
        val response = stub.binaryRpc("Long" to "world")
        val str = response.readRemaining().readText()
        assertEquals(longLongContent, str)
    }

    @Test
    fun testBinaryInputTest() = runBlockingUnit {
        val stub = createService()
        val response = stub.inputRpc(ByteReadChannel("Hello world".encodeToByteArray()))
        assertEquals("Input: Hello world", response)
        assertEquals("pong", stub.ping("ping"))
    }

    @Test
    fun testSubService() = runBlockingUnit {
        val stub = createService()
        assertEquals(
            "oh, Hello world",
            stub.subservice("oh,").rpc("Hello" to "world")
        )
    }

    @Test
    fun testSubServiceTwoCalls() = runBlockingUnit {
        val stub = createService()
        stub.subservice("oh,").rpc("Hello" to "world")
        assertEquals(
            "oh, Hello world",
            stub.subservice("oh,").rpc("Hello" to "world")
        )
    }

    private fun extension(): String =
        if (NativeUtils::class.java.getResourceAsStream("/libs/libksrpc_test.so") != null) {
            "so"
        } else {
            "dylib"
        }

    private suspend fun CoroutineScope.createService(): JniTestInterface {
        NativeUtils.loadLibraryFromJar("/libs/libksrpc_test.${extension()}")
        val env = ksrpcEnvironment(JniSerialization()) {}
        val nativeEnvironment = NativeHost().createEnv()
        val connection = JniConnection(this, env, nativeEnvironment)
        suspendCoroutine<Int> {
            NativeHost().registerService(connection, it.withConverter(newTypeConverter<Any?>().int))
        }
        return connection.defaultChannel().toStub<JniTestInterface, JniSerialized>()
    }
}
