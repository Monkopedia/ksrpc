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
@file:Suppress("MemberVisibilityCanBePrivate")

package com.monkopedia.ksrpc.jni

import com.monkopedia.ksrpc.RpcFailure
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class JavaJniContinuation<T>(
    private val converter: Converter<Any?, T>,
    private val continuation: Continuation<T>
) {
    var ser: JniSer = JniSer

    fun resumeSuccess(result: Any?) {
        val value = converter.convertTo(result)
        continuation.resume(value)
    }

    fun resumeFailure(result: Any?) {
        val value = failureConverter.convertTo(result)
        continuation.resumeWithException(value.toException())
    }

    companion object {
        internal val failureConverter = JniSer.converterOf(RpcFailure.serializer()).java
    }
}

@Suppress("UNCHECKED_CAST")
internal val <N> Converter<*, N>.java: Converter<Any?, N>
    get() = this as Converter<Any?, N>

fun <T> Continuation<T>.withConverter(converter: Converter<*, T>): JavaJniContinuation<T> {
    JNIControl.ensureInit()
    return JavaJniContinuation(converter.java, this)
}
