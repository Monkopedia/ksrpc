/*
 * Copyright (C) 2026 Jason Monk <monkopedia@gmail.com>
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
@file:Suppress("ktlint:standard:filename")

package com.monkopedia.jnitest

import com.monkopedia.jni.JNIEnvVar
import com.monkopedia.jni.JNINativeInterface_
import com.monkopedia.ksrpc.jni.initThread as movedInitThread
import com.monkopedia.ksrpc.jni.threadEnv as movedThreadEnv
import com.monkopedia.ksrpc.jni.threadJni as movedThreadJni
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVarOf
import kotlinx.cinterop.ExperimentalForeignApi

/**
 * @deprecated Moved to [com.monkopedia.ksrpc.jni.threadJni].
 */
@Deprecated(
    "Moved to com.monkopedia.ksrpc.jni",
    ReplaceWith("threadJni", "com.monkopedia.ksrpc.jni.threadJni")
)
val threadJni: JNINativeInterface_
    get() = movedThreadJni

/**
 * @deprecated Moved to [com.monkopedia.ksrpc.jni.threadEnv].
 */
@Deprecated(
    "Moved to com.monkopedia.ksrpc.jni",
    ReplaceWith("threadEnv", "com.monkopedia.ksrpc.jni.threadEnv")
)
val threadEnv: CPointer<CPointerVarOf<CPointer<JNINativeInterface_>>>
    get() = movedThreadEnv

/**
 * @deprecated Moved to [com.monkopedia.ksrpc.jni.initThread].
 */
@Deprecated(
    "Moved to com.monkopedia.ksrpc.jni",
    ReplaceWith("initThread(e)", "com.monkopedia.ksrpc.jni.initThread")
)
fun initThread(e: CPointer<JNIEnvVar>) = movedInitThread(e)
