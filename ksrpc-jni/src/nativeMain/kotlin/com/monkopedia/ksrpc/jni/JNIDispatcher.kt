/**
 * Copyright (C) 2024 Jason Monk <monkopedia@gmail.com>
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
@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.jnitest.com.monkopedia.ksrpc.jni

import com.monkopedia.jni.JNIEnvVar
import com.monkopedia.jni.jint
import com.monkopedia.jni.jobject
import com.monkopedia.jnitest.JNI
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.channels.onSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import platform.posix.usleep
import kotlin.coroutines.CoroutineContext
import kotlin.native.concurrent.ThreadLocal

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
