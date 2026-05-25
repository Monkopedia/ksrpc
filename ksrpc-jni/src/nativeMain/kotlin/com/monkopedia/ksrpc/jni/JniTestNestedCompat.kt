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
@file:Suppress("ktlint:standard:filename")

package com.monkopedia.jnitest.com.monkopedia.ksrpc.jni

/**
 * @deprecated Moved to [com.monkopedia.ksrpc.jni.JNIDispatcher].
 */
@Deprecated(
    "Moved to com.monkopedia.ksrpc.jni",
    ReplaceWith("JNIDispatcher", "com.monkopedia.ksrpc.jni.JNIDispatcher")
)
typealias JNIDispatcher = com.monkopedia.ksrpc.jni.JNIDispatcher

/**
 * @deprecated Moved to [com.monkopedia.ksrpc.jni.JavaJniContinuation].
 */
@Deprecated(
    "Moved to com.monkopedia.ksrpc.jni",
    ReplaceWith("JavaJniContinuation", "com.monkopedia.ksrpc.jni.JavaJniContinuation")
)
typealias JavaJniContinuation<T> = com.monkopedia.ksrpc.jni.JavaJniContinuation<T>

/**
 * @deprecated Moved to [com.monkopedia.ksrpc.jni.JavaJniContinuationConverter].
 */
@Deprecated(
    "Moved to com.monkopedia.ksrpc.jni",
    ReplaceWith(
        "JavaJniContinuationConverter",
        "com.monkopedia.ksrpc.jni.JavaJniContinuationConverter"
    )
)
typealias JavaJniContinuationConverter<T> =
    com.monkopedia.ksrpc.jni.JavaJniContinuationConverter<T>
