@file:Suppress("MemberVisibilityCanBePrivate")

package com.monkopedia.ksrpc.jni

import com.monkopedia.ksrpc.RpcFailure
import com.monkopedia.ksrpc.asString
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

class NativeJniContinuation<T>(val nativeObject: Long) : JniContinuation<T> {
    var ser: JniSer = JniSer

    protected fun finalize() {
        finalize(nativeObject)
    }

    override fun resumeWith(converter: Converter<*, T>, result: Result<T>) {
        result.onSuccess {
            try {
                resumeSuccess(nativeObject, converter.convertFrom(it))
            } catch (t: Throwable) {
                throw Throwable("resumeWith failed", t)
            }
        }.onFailure {
            val rpcFailure = RpcFailure(it.asString)
            resumeFailure(nativeObject, ser.encodeToJni(RpcFailure.serializer(), rpcFailure))
        }
    }

    override fun asCompletion(int: Converter<Any, T>): Continuation<T> {
        return object : Continuation<T> {
            override val context: CoroutineContext = EmptyCoroutineContext

            override fun resumeWith(result: Result<T>) {
                resumeWith(int, result)
            }
        }
    }

    external fun finalize(nativeObject: Long)
    external fun resumeSuccess(nativeObject: Long, value: Any?)
    external fun resumeFailure(nativeObject: Long, value: JniSerialized)
}