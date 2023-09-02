package com.monkopedia.ksrpc.jni

import kotlinx.serialization.serializer
import kotlin.coroutines.Continuation

interface JniContinuation<T> {
    fun resumeWith(converter: Converter<*, T>, result: Result<T>)

    fun asCompletion(int: Converter<Any, T>): Continuation<T>
}

inline fun <reified T> JniContinuation<T>.resumeWith(result: Result<T>, jniSer: JniSer = JniSer) {
    resumeWith(jniSer.converterOf(serializer()), result)
}