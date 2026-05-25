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
@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
@file:Suppress("unused")

package com.monkopedia.ksrpc.samples

import com.monkopedia.jni.JNIEnvVar
import com.monkopedia.jni.jobject
import com.monkopedia.ksrpc.channels.registerDefault
import com.monkopedia.ksrpc.jni.ksrpcHostConnection
import com.monkopedia.ksrpc.serialized
import kotlin.experimental.ExperimentalNativeApi
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi

/**
 * Native host side of the JNI transport sample.
 *
 * This is the `@CName` export that backs `MyNativeService.initialize` on the JVM
 * side. Its symbol is derived from the declaring JVM class, so it lives under the
 * sample's package. It forwards the `JniHostInit`
 * (delivered as the raw `host` jobject) to `ksrpcHostConnection`, which builds the
 * per-connection environment and runs the `setup` lambda that registers the
 * service this connection hosts.
 */
@CName("Java_com_monkopedia_ksrpc_samples_MyNativeService_initialize")
fun initialize(env: CPointer<JNIEnvVar>, clazz: jobject, host: jobject) =
    ksrpcHostConnection(env, host) { conn ->
        val service = object : GreetingService {
            override suspend fun greet(name: String): String = "Hello, $name!"
        }
        conn.registerDefault(service.serialized(conn.env))
    }
