@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.jnitest.com.monkopedia.ksrpc.jni

import com.monkopedia.jni.JNIEnvVar
import com.monkopedia.jni.jint
import com.monkopedia.jni.jobject
import com.monkopedia.jnitest.JNI
import kotlin.coroutines.CoroutineContext
import kotlin.native.concurrent.ThreadLocal
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.channels.onSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import platform.posix.usleep

@ThreadLocal
private var dispatchThread = false

object JNIDispatcher : CoroutineDispatcher() {
    private val channel = Channel<Runnable>()
    private val threadCount = MutableStateFlow(0)
    private val sendThread = newSingleThreadContext("sender")

    internal fun updateThreads(count: Int) {
        threadCount.value = count
    }

    internal fun executeThread(id: Int) {
        try {
            dispatchThread = true
            while (id < threadCount.value) {
                channel.tryReceive().onSuccess {
                    it.run()
                }.onFailure {
                    usleep(10000u)
                }
            }
        } catch (cancel: BreakExecutionException) {
            // Expected
        }
    }

    override fun isDispatchNeeded(context: CoroutineContext): Boolean {
        return !dispatchThread
    }

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        GlobalScope.launch(sendThread) {
            channel.send(block)
        }
    }
}

private class BreakExecutionException : Exception()

@CName("Java_com_monkopedia_ksrpc_jni_JNIControl_updateThreads")
fun jniUpdateThreads(env: CPointer<JNIEnvVar>, clazz: jobject, threads: jint) {
    try {
        JNI.init(env)
        JNIDispatcher.updateThreads(threads)
    } catch (t: Throwable) {
        println("Update caught exception: $t")
        t.printStackTrace()
    }
}

@CName("Java_com_monkopedia_ksrpc_jni_JNIControl_executeThread")
fun jniExecuteThread(env: CPointer<JNIEnvVar>, clazz: jobject, id: jint) {
    try {
        JNI.init(env)
        JNIDispatcher.executeThread(id)
    } catch (t: Throwable) {
        println("Execute caught exception: $t")
        t.printStackTrace()
        usleep(1000000u)
    }
}
