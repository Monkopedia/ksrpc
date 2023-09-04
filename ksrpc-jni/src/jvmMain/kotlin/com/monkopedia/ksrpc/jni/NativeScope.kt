package com.monkopedia.ksrpc.jni.com.monkopedia.ksrpc.jni

import com.monkopedia.ksrpc.jni.com.monkopedia.ksrpc.jni.NativeScopeHandler.cancelScope
import com.monkopedia.ksrpc.jni.com.monkopedia.ksrpc.jni.NativeScopeHandler.createNativeScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job

/**
 * Maps the lifecycle of a scope into a matching native scope.
 *
 * Note that this adopts the behavior of a SupervisorJob where the
 * cancellation of the native stop does not cancel the receiver scope.
 */
val CoroutineScope.asNativeScope: Long
    get() {
        val nativeScope = createNativeScope()
        coroutineContext.job.invokeOnCompletion {
            cancelScope(nativeScope)
        }
        return nativeScope
    }

object NativeScopeHandler {
    external fun createNativeScope(): Long
    external fun cancelScope(scope: Long)
}
