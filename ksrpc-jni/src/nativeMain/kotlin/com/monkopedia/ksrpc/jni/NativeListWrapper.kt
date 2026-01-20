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
@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.ksrpc.jni

import com.monkopedia.jni.JNIEnvVar
import com.monkopedia.jni.jobject
import com.monkopedia.jnitest.JNI
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi

internal open class NativeListWrapper(val list: jobject) : BasicList<jobject?> {
    override val asSerialized: JniSerialized
        get() = JniSerialized(this)

    override val size: Int
        get() = JNI.List.size(list)

    override fun get(index: Int): jobject? = JNI.List.get(list, index)

    override fun toString(): String = JNI.Obj.toString(list) ?: "null"
}

internal class NativeMutableListWrapper(
    list: jobject = JNI.ArrayList.new() ?: error("Failed to initialize list")
) : NativeListWrapper(list),
    MutableBasicList<jobject?> {

    override fun set(index: Int, value: jobject?) {
        JNI.ArrayList.set(list, index, value)
    }

    override fun add(value: jobject?) {
        JNI.ArrayList.add(list, value)
    }

    override fun toString(): String = JNI.Obj.toString(list) ?: "null"
}

actual fun <T> newList(): MutableBasicList<T> {
    @Suppress("UNCHECKED_CAST")
    return NativeMutableListWrapper() as MutableBasicList<T>
}

fun JniSerialized.Companion.fromJvm(
    jniEnv: CPointer<JNIEnvVar>,
    serialized: jobject
): JniSerialized {
    JNI.init(jniEnv)
    return NativeListWrapper(JNI.JavaListWrapperKt.toList(serialized)!!).asSerialized
}

fun JniSerialized.toJvm(jniEnv: CPointer<JNIEnvVar>): jobject {
    JNI.init(jniEnv)
    val list = (list as NativeListWrapper).list
    return JNI.JavaListWrapperKt.toSerialized(list)!!
}
