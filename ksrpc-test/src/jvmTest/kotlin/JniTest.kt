package com.monkopedia.ksrpc

import ComplexClass
import OtherClass
import com.monkopedia.ksrpc.jni.JniSer
import com.monkopedia.ksrpc.jni.NativeUtils
import com.monkopedia.ksrpc.jni.decodeFromJni
import com.monkopedia.ksrpc.jni.encodeToJni
import com.monkopedia.ksrpc.jni.newTypeConverter
import kotlinx.coroutines.CompletableDeferred
import kotlin.coroutines.resume
import kotlin.test.Test
import kotlin.test.assertEquals

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
        println("Result: $result")
        val decoded = JniSer.decodeFromJni<ComplexClass>(result)
        println("Decoded: $decoded")
        assertEquals(decoded, encode)
        val nativeVersion = NativeHost().serializeDeserialize(result)
        println("Native: $nativeVersion")
        val nativeDecoded = JniSer.decodeFromJni<ComplexClass>(nativeVersion)
        println("Native version $nativeDecoded")
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
            it.asCompletion(newTypeConverter<Any>().int)
        }
        continuations[0].resume(2)
        continuations[1].resume(3)
        continuations[2].resume(4)
        assertEquals("Result: 2", results[0].await())
        assertEquals("3 + 4 = 7", results[1].await())
    }
}
