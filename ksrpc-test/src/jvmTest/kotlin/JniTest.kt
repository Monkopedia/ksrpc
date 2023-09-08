package com.monkopedia.ksrpc

import ComplexClass
import OtherClass
import com.monkopedia.ksrpc.jni.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch

class JniTest {

    @Test
    fun testSerialization() {
        NativeUtils.loadLibraryFromJar("/libs/libksrpc_test.so")
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
        NativeUtils.loadLibraryFromJar("/libs/libksrpc_test.so")
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
        NativeUtils.loadLibraryFromJar("/libs/libksrpc_test.so")
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
        NativeUtils.loadLibraryFromJar("/libs/libksrpc_test.so")
        val env = ksrpcEnvironment(JniSerialization()) {}
        val nativeEnvironment = NativeHost().createEnv()
        val connection = JniConnection(this, env, nativeEnvironment)
        suspendCoroutine<Int> {
            NativeHost().registerService(connection, it.withConverter(newTypeConverter<Any?>().int))
        }
        val service = connection.defaultChannel().toStub<JniTestInterface, JniSerialized>()
        val result = service.ping("ping")
        assertEquals("pong", result)
    }
}
