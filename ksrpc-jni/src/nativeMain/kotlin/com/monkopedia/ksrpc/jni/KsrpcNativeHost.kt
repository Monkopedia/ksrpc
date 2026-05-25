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
@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class, KsrpcInternal::class)

package com.monkopedia.ksrpc.jni

import com.monkopedia.jni.JNIEnvVar
import com.monkopedia.jni.jobject
import com.monkopedia.ksrpc.KsrpcEnvironmentBuilder
import com.monkopedia.ksrpc.annotation.KsrpcInternal
import com.monkopedia.ksrpc.channels.Connection
import com.monkopedia.ksrpc.ksrpcEnvironment
import kotlin.experimental.ExperimentalNativeApi
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.invoke
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.toLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import platform.posix.usleep

/**
 * Hosts a single ksrpc [Connection] inside a Kotlin/Native shared library and
 * hands the JVM the opaque native handle that backs it.
 *
 * The consumer declares the binding as an `external fun` on one of their own JVM
 * classes and passes a reference to it into
 * [com.monkopedia.ksrpc.jni.KsrpcNativeHost.connect]; the matching native
 * `@CName` export is named after that class and delegates here:
 *
 * ```
 * // backs `external fun initialize(host: JniHostInit)` on
 * // the consumer's class `com.example.MyNativeService`:
 * @CName("Java_com_example_MyNativeService_initialize")
 * fun initialize(env: CPointer<JNIEnvVar>, clazz: jobject, host: jobject) =
 *     ksrpcHostConnection(env, host) { conn ->
 *         conn.registerDefault(MyServiceImpl().serialized(conn.env))
 *     }
 * ```
 *
 * For each connection it builds a [KsrpcEnvironment][com.monkopedia.ksrpc.KsrpcEnvironment]
 * (pre-set to [JniSerialization]; the [configure] block tunes the logger, error
 * listener, coroutine exception handler, ... and the serializer is fixed because
 * the API is typed on [JniSerialized]), wraps the JVM connection object, runs
 * [setup], and returns a single native handle (a [StableRef] to the
 * [NativeConnection]) that the JVM passes back to dispose the connection.
 *
 * The [setup] lambda runs once per connection, on the JNI dispatcher, with the
 * freshly-opened [Connection] so it can register the service(s) this connection
 * hosts. Each connection gets its own environment and service instance(s).
 *
 * @sample com.monkopedia.ksrpc.samples.initialize
 */
fun ksrpcHostConnection(
    env: CPointer<JNIEnvVar>,
    host: jobject,
    configure: KsrpcEnvironmentBuilder<JniSerialized>.() -> Unit = {},
    setup: suspend (Connection<JniSerialized>) -> Unit
) {
    initThread(env)
    try {
        val connection = JNI.JniHostInit.connection(host)
            ?: error("JniHostInit.connection was null")
        val output = JNI.JniHostInit.output(host)
            ?: error("JniHostInit.output was null")
        val nativeScope = JNI.JniHostInit.scope(host).toCPointer<CPointed>()
            ?.asStableRef<CoroutineScope>()?.get()
            ?: error("Invalid native scope handle")
        val ksrpcEnv = ksrpcEnvironment(JniSerialization(), configure)
        val objectRef = threadJni.NewWeakGlobalRef!!.invoke(env, connection)
            ?: error("Could not create weak global ref to JVM connection")
        val nativeConnection = NativeConnection(nativeScope, objectRef, ksrpcEnv)
        val handle = StableRef.create(nativeConnection).asCPointer().rawValue.toLong()
        val continuation = JavaJniContinuationConverter<Long>(env).convertTo(output)
        GlobalScope.launch(JNIDispatcher) {
            runCatching {
                setup(nativeConnection)
                handle
            }.let {
                continuation.resumeWith(newTypeConverter<jobject>().long, it)
            }
        }
    } catch (t: Throwable) {
        t.printStackTrace()
        usleep(1000000u)
    }
}
