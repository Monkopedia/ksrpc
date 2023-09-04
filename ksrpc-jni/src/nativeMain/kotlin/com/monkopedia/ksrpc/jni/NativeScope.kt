package com.monkopedia.jnitest.com.monkopedia.ksrpc.jni

import com.monkopedia.jni.JNIEnvVar
import com.monkopedia.jni.jlong
import com.monkopedia.jni.jobject
import com.monkopedia.jnitest.initThread
import kotlinx.cinterop.*
import kotlinx.coroutines.*

@OptIn(ExperimentalForeignApi::class)
@CName("Java_com_monkopedia_ksrpc_jni_NativeScopeHandler_createNativeScope")
fun createNativeScope(env: CPointer<JNIEnvVar>, clazz: jobject): jlong {
    initThread(env)
    try {
        val scope = CoroutineScope(SupervisorJob() + JNIDispatcher)
        val stableRef = StableRef.create(scope).asCPointer()
        return stableRef.rawValue.toLong()
    } catch (t: Throwable) {
        t.printStackTrace()
        return -1
    }
}

@OptIn(ExperimentalForeignApi::class)
@CName("Java_com_monkopedia_ksrpc_jni_NativeScopeHandler_cancelScope")
fun cancelScope(env: CPointer<JNIEnvVar>, clazz: jobject, scope: jlong) {
    initThread(env)
    try {
        val ptr = scope.toCPointer<CPointed>()?.asStableRef<CoroutineScope>()?.get()
            ?: return
        ptr.cancel()
    } catch (t: Throwable) {
        t.printStackTrace()
    }
}
