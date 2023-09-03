@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.ksrpc.jni

import com.monkopedia.jni.JNIEnvVar
import com.monkopedia.jni.jlong
import com.monkopedia.jni.jobject
import com.monkopedia.jnitest.JNI
import com.monkopedia.ksrpc.RpcFailure
import kotlin.coroutines.Continuation
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.toCPointer

@Suppress("UNCHECKED_CAST")
internal val <N> Converter<*, N>.native: Converter<jobject?, N>
    get() = this as Converter<jobject?, N>

class NativeJniContinuation<T>(
    private val continuation: Continuation<T>,
    internal val typeConverter: Converter<jobject?, T>
) {
    fun resumeWith(result: Result<T>) {
        println("Start resume $result")
        continuation.resumeWith(result)
        println("End resume $result")
    }

    companion object {
        internal val failureConverter = JniSer.converterOf(RpcFailure.serializer()).native
    }
}

fun <T> Continuation<T>.withConverter(converter: Converter<*, T>): NativeJniContinuation<T> {
    return NativeJniContinuation(this, converter.native)
}

class NativeJniContinuationConverter<T>(env: CPointer<JNIEnvVar>) :
    Converter<jobject?, NativeJniContinuation<T>> {
    init {
        JNI.init(env)
    }

    override fun convertTo(rawValue: jobject?): NativeJniContinuation<T> {
        val nativeObject =
            JNI.NativeJniContinuation.getNativeObject(rawValue ?: error("No java object given"))
        return nativeObject.toCPointer<CPointed>()?.asStableRef<NativeJniContinuation<T>>()?.get()
            ?: error("Cannot decode pointer")
    }

    override fun convertFrom(value: NativeJniContinuation<T>): jobject? {
        val input = StableRef.create(value).asCPointer().rawValue.toLong()
        println("Calling new with $input")
        return JNI.NativeJniContinuation.new(input)
    }
}

@OptIn(ExperimentalForeignApi::class)
@CName("Java_com_monkopedia_ksrpc_jni_NativeJniContinuation_resumeSuccess")
fun resumeSuccess(env: CPointer<JNIEnvVar>, clazz: jobject, nativeObject: jlong, input: jobject) {
    try {
        val ptr = nativeObject.toCPointer<CPointed>()?.asStableRef<NativeJniContinuation<Any>>()?.get()
            ?: return
        val result = Result.success(ptr.typeConverter.convertTo(input))
        ptr.resumeWith(result)
    } catch (t: Throwable) {
        t.printStackTrace()
    }
}

@OptIn(ExperimentalForeignApi::class)
@CName("Java_com_monkopedia_ksrpc_jni_NativeJniContinuation_resumeFailure")
fun resumeFailure(env: CPointer<JNIEnvVar>, clazz: jobject, nativeObject: jlong, input: jobject) {
    try {
        val ptr = nativeObject.toCPointer<CPointed>()?.asStableRef<NativeJniContinuation<Any>>()?.get()
            ?: return
        val failure = Result.failure<Any>(
            NativeJniContinuation.failureConverter.convertTo(input).toException()
        )
        ptr.resumeWith(failure)
    } catch (t: Throwable) {
        t.printStackTrace()
    }
}

@OptIn(ExperimentalForeignApi::class)
@CName("Java_com_monkopedia_ksrpc_jni_NativeJniContinuation_finalize")
fun finalize(env: CPointer<JNIEnvVar>, clazz: jobject, nativeObject: jlong) {
    try {
        val ptr = nativeObject.toCPointer<CPointed>()?.asStableRef<NativeJniContinuation<Any>>()
            ?: return
        ptr.dispose()
    } catch (t: Throwable) {
        t.printStackTrace()
    }
}
