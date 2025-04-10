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
package com.monkopedia.ksrpc.jni

import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.serialization.serializer

interface JniContinuation<T> {
    fun resumeWith(converter: Converter<*, T>, result: Result<T>)
}

fun <T> JniContinuation<T>.asContinuation(int: Converter<*, T>): Continuation<T> {
    return object : Continuation<T> {
        override val context: CoroutineContext = EmptyCoroutineContext

        override fun resumeWith(result: Result<T>) {
            resumeWith(int, result)
        }
    }
}

inline fun <reified T> JniContinuation<T>.resumeWith(result: Result<T>, jniSer: JniSer = JniSer) {
    resumeWith(jniSer.converterOf(serializer()), result)
}
