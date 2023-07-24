@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.ksrpc.jni

import com.monkopedia.jni.JNIEnvVar
import com.monkopedia.jni.jobject
import com.monkopedia.jnitest.JNI
import com.monkopedia.jnitest.JNI.toStr
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi

internal open class NativeListWrapper(val list: jobject) : BasicList<jobject?> {
    override val asSerialized: JniSerialized
        get() = JniSerialized(this)

    override val size: Int
        get() = JNI.sizeOfList(list)

    override fun get(index: Int): jobject? {
        return JNI.getFromList(list, index)
    }

    override fun toString(): String {
        return list.toStr() ?: "null"
    }
}

internal class NativeMutableListWrapper(list: jobject = JNI.newList() ?: error("Failed to initialize list")) :
    NativeListWrapper(list), MutableBasicList<jobject?> {

    override fun set(index: Int, value: jobject?) {
        JNI.setInList(list, index, value)
    }

    override fun add(value: jobject?) {
        JNI.addToList(list, value)
    }

    override fun toString(): String {
        return list.toStr() ?: "null"
    }
}

actual fun <T> newList(): MutableBasicList<T> {
    @Suppress("UNCHECKED_CAST")
    return NativeMutableListWrapper() as MutableBasicList<T>
}

fun toSerialized(jniEnv: CPointer<JNIEnvVar>, serialized: jobject): JniSerialized {
    JNI.init(jniEnv)
    return NativeListWrapper(JNI.toList(serialized)).asSerialized
}

fun toJniReturn(jniEnv: CPointer<JNIEnvVar>, serialized: JniSerialized): jobject {
    JNI.init(jniEnv)
    val list = (serialized.list as NativeListWrapper).list
    return JNI.toSerialized(list)
}