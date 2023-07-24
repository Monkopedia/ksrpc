package com.monkopedia.jnitest

import ComplexClass
import com.monkopedia.jni.JNIEnvVar
import com.monkopedia.jni.jclass
import com.monkopedia.jni.jobject
import com.monkopedia.ksrpc.jni.JniSer
import com.monkopedia.ksrpc.jni.decodeFromJni
import com.monkopedia.ksrpc.jni.encodeToJni
import com.monkopedia.ksrpc.jni.toJniReturn
import com.monkopedia.ksrpc.jni.toSerialized
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.usleep

@OptIn(ExperimentalForeignApi::class)
@CName("Java_com_monkopedia_jnitest_NativeHost_serializeDeserialize")
fun serializeDeserialize(env: CPointer<JNIEnvVar>, clazz: jclass, input: jobject): jobject {
    try {
        val jniSerialized = toSerialized(env, input)
        val obj = JniSer.decodeFromJni<ComplexClass>(jniSerialized)
        println("Found $obj")
        val output = obj.copy(intValue = (obj.intValue ?: 0) + 1)
        return toJniReturn(env, JniSer.encodeToJni(output))
    } catch (t: Throwable) {
        t.printStackTrace()
        usleep(1000000u)
        throw t
    }
}

