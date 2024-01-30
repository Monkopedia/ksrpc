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
@file:OptIn(ExperimentalNativeApi::class)

package com.monkopedia.jnitest.com.monkopedia.ksrpc.jni

import com.monkopedia.jni.JNIEnvVar
import com.monkopedia.jni.jlong
import com.monkopedia.jni.jobject
import com.monkopedia.jnitest.initThread
import kotlin.experimental.ExperimentalNativeApi
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.toCPointer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

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
