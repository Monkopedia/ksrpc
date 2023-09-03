@file:Suppress("MemberVisibilityCanBePrivate")

package com.monkopedia.ksrpc.jni

import com.monkopedia.ksrpc.RpcFailure
import kotlin.coroutines.*

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
