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
import com.monkopedia.jni.jlong
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
 * This is the one piece of glue a consumer needs to write to host a ksrpc
 * service in native code. The consumer declares the binding as an `external fun`
 * on one of *their own* JVM classes and passes a reference to it into
 * [com.monkopedia.ksrpc.jni.KsrpcNativeHost.connect]; the matching native
 * `@CName` export is therefore named after the consumer's class (not a ksrpc
 * type) and simply delegates here:
 *
 * ```
 * // matches the consumer's own JVM class `com.example.MyNativeService`:
 * @CName("Java_com_example_MyNativeService_initialize")
 * fun initialize(
 *     env: CPointer<JNIEnvVar>,
 *     clazz: jobject,
 *     connection: jobject,
 *     scope: jlong,
 *     output: jobject
 * ) = ksrpcHostConnection(env, connection, scope, output) { conn ->
 *     conn.registerDefault(MyServiceImpl().serialized(conn.env))
 * }
 * ```
 *
 * ksrpc owns everything else: it builds this connection's own
 * [KsrpcEnvironment][com.monkopedia.ksrpc.KsrpcEnvironment] (pre-set to
 * [JniSerialization]; the [configure] block can tune logger, error listener,
 * coroutine exception handler, ... but cannot swap the serializer because the
 * whole API is typed on [JniSerialized]), wraps the JVM connection object, and
 * manages the connection's lifecycle. The JVM holds a single opaque native
 * handle (a [StableRef] to the [NativeConnection]) which it later passes back to
 * dispose the connection.
 *
 * The [setup] lambda runs **once per connection**, on the JNI dispatcher, with
 * the freshly-opened [Connection] so it can register the service(s) this
 * connection hosts. There is no global state and nothing is shared across
 * connections: every connection gets its own environment and its own service
 * instance(s).
 */
fun ksrpcHostConnection(
    env: CPointer<JNIEnvVar>,
    connection: jobject,
    scope: jlong,
    output: jobject,
    configure: KsrpcEnvironmentBuilder<JniSerialized>.() -> Unit = {},
    setup: suspend (Connection<JniSerialized>) -> Unit
) {
    initThread(env)
    try {
        val nativeScope = scope.toCPointer<CPointed>()?.asStableRef<CoroutineScope>()?.get()
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
