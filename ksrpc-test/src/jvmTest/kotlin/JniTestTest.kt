package com.monkopedia.ksrpc

import ComplexClass
import OtherClass
import com.monkopedia.jnitest.NativeHost
import com.monkopedia.jnitest.NativeUtils
import com.monkopedia.ksrpc.jni.JniSer
import com.monkopedia.ksrpc.jni.decodeFromJni
import com.monkopedia.ksrpc.jni.encodeToJni
import kotlin.test.Test
import kotlin.test.assertEquals

class JniTestTest {

    @Test
    fun testJni() {
        NativeUtils.loadLibraryFromJar("/libs/libksrpc_jni.so")
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
}
