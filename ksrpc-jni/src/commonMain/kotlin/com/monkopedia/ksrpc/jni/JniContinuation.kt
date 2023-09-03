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
