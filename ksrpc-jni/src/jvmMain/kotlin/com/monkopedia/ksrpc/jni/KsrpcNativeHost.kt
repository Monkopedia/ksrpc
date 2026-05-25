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
package com.monkopedia.ksrpc.jni

import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.ksrpcEnvironment
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CoroutineScope

/**
 * JVM entry point for talking to a ksrpc service hosted inside a Kotlin/Native
 * shared library.
 *
 * The consumer's `.so` provides a single
 * `@CName("Java_com_monkopedia_ksrpc_jni_KsrpcNativeHost_initialize")` symbol
 * (typically by delegating to
 * [ksrpcHostConnection][com.monkopedia.ksrpc.jni.ksrpcHostConnection]); it is
 * resolved by JNI name mangling against the [initialize] `external fun` here, so
 * the consumer declares no `external fun`s of its own.
 *
 * The library must already be loaded (e.g. via
 * [NativeUtils.loadLibraryFromJar]). Typical use:
 *
 * ```
 * NativeUtils.loadLibraryFromJar("/libmyservice.so")
 * // create + register the native service on a single connection (connect does both)
 * val connection = KsrpcNativeHost.connect(scope)
 * // use the connection
 * val service = connection.defaultChannel().toStub<MyService, JniSerialized>()
 * ```
 */
object KsrpcNativeHost {

    /**
     * Opens a [Connection] to the native host on the given [scope]: it builds the
     * connection, drives the native `initialize` so the host's `setup` lambda runs
     * against *this* connection (building its own per-connection native
     * environment and service instance(s)), and returns it ready to use. Each call
     * is independent -- nothing is shared across connections.
     *
     * The connection uses [JniSerialization]; the optional [environment]
     * configures the JVM-side [KsrpcEnvironment] (logger, error listener, ...). The
     * native side builds its own per-connection environment, configured via the
     * `configure` block passed to
     * [ksrpcHostConnection][com.monkopedia.ksrpc.jni.ksrpcHostConnection].
     */
    suspend fun connect(
        scope: CoroutineScope,
        environment: KsrpcEnvironment<JniSerialized> = ksrpcEnvironment(JniSerialization()) {}
    ): JniConnection {
        val connection = JniConnection(scope, environment)
        val handle = suspendCoroutine<Long> {
            initialize(
                connection,
                scope.asNativeScope,
                it.withConverter(newTypeConverter<Any?>().long)
            )
        }
        connection.attachNative(handle)
        return connection
    }

    private external fun initialize(
        connection: JniConnection,
        scope: Long,
        output: JavaJniContinuation<Long>
    )
}
