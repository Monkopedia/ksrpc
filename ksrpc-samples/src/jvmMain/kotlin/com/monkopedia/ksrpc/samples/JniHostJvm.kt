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
@file:Suppress("unused", "UNUSED_VARIABLE")

package com.monkopedia.ksrpc.samples

import com.monkopedia.ksrpc.jni.JniHostInit
import com.monkopedia.ksrpc.jni.JniSerialized
import com.monkopedia.ksrpc.jni.KsrpcNativeHost
import com.monkopedia.ksrpc.jni.NativeUtils
import com.monkopedia.ksrpc.toStub
import kotlinx.coroutines.CoroutineScope

/**
 * The JVM-side binding for the native host. The `@CName` export in the native
 * source set (`Java_com_monkopedia_ksrpc_samples_MyNativeService_initialize`) is
 * named after this class, so the symbol lands under the sample's package.
 */
object MyNativeService {
    external fun initialize(host: JniHostInit)
}

/**
 * Demonstrates connecting to a ksrpc service hosted inside a Kotlin/Native shared
 * library over the JNI transport.
 */
suspend fun jniHostConnect(scope: CoroutineScope) {
    // Load the Kotlin/Native shared library bundled on the classpath (once per process).
    NativeUtils.loadLibraryFromJar("/libs/libksrpc_samples.so")

    // connect() invokes MyNativeService::initialize, which drives the native
    // `ksrpcHostConnection` to build this connection's environment and register
    // its service before returning a connection ready to use.
    val connection = KsrpcNativeHost.connect(scope, MyNativeService::initialize)

    // Use the connection.
    val service = connection.defaultChannel().toStub<GreetingService, JniSerialized>()
    val greeting = service.greet("world")
}
