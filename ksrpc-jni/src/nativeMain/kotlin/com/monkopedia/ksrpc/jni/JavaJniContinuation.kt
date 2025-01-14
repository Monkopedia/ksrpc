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
@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.jnitest.com.monkopedia.ksrpc.jni

import com.monkopedia.jni.JNIEnvVar
import com.monkopedia.jni.jobject
import com.monkopedia.jnitest.JNI
import com.monkopedia.jnitest.threadEnv
import com.monkopedia.jnitest.threadJni
import com.monkopedia.ksrpc.RpcFailure
import com.monkopedia.ksrpc.asString
import com.monkopedia.ksrpc.jni.Converter
import com.monkopedia.ksrpc.jni.JniContinuation
import com.monkopedia.ksrpc.jni.NativeJniContinuation
import com.monkopedia.ksrpc.jni.native
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.invoke

class JavaJniContinuation<T>(java: jobject) : JniContinuation<T> {

    internal val java = threadJni.NewGlobalRef!!.invoke(threadEnv, java)!!

    override fun resumeWith(converter: Converter<*, T>, result: Result<T>) {
        result.onSuccess {
            JNI.JavaJniContinuation.resumeSuccess(java, converter.native.convertFrom(it))
        }.onFailure {
            JNI.JavaJniContinuation.resumeFailure(
                java,
                NativeJniContinuation.failureConverter.convertFrom(RpcFailure(it.asString))
            )
        }.also {
            threadJni.DeleteGlobalRef!!.invoke(threadEnv, java)
        }
    }
}

class JavaJniContinuationConverter<T>(env: CPointer<JNIEnvVar>) :
    Converter<jobject?, JavaJniContinuation<T>> {
    init {
        JNI.init(env)
    }

    override fun convertTo(rawValue: jobject?): JavaJniContinuation<T> {
        return JavaJniContinuation(rawValue ?: error("Missing value"))
    }

    override fun convertFrom(value: JavaJniContinuation<T>): jobject? {
        return value.java
    }
}
