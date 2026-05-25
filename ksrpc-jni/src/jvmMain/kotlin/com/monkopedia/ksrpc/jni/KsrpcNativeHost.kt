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
 * shared library that registered itself with
 * [ksrpcNativeHost][com.monkopedia.ksrpc.jni.ksrpcNativeHost].
 *
 * ksrpc owns the JNI exports backing [createEnv] and [registerService]; they are
 * resolved by JNI name mangling against the symbols `ksrpc-jni`'s klib links into
 * the consumer's `.so`, so the consumer declares no `external fun`s of its own.
 *
 * The library must already be loaded (e.g. via
 * [NativeUtils.loadLibraryFromJar]). Typical use:
 *
 * ```
 * NativeUtils.loadLibraryFromJar("/libmyservice.so")
 * val connection = KsrpcNativeHost.connect(scope)
 * val service = connection.defaultChannel().toStub<MyService, JniSerialized>()
 * ```
 */
object KsrpcNativeHost {
    private external fun createEnv(): Long
    private external fun registerService(
        connection: JniConnection,
        output: JavaJniContinuation<Int>
    )

    /**
     * Opens a [Connection] to the native host on the given [scope]: it creates the
     * connection (with a fresh native environment), drives the native registration
     * so the host's `register` lambda runs against *this* connection, and returns
     * it ready to use. Each call is independent -- a new connection gets its own
     * native environment and service instance(s); nothing is shared across calls.
     *
     * The connection uses [JniSerialization]; the optional [environment]
     * configures the JVM-side [KsrpcEnvironment] (logger, error listener, ...).
     * The native side builds its own per-connection environment, configured via
     * the `configure` block passed to [ksrpcNativeHost].
     */
    suspend fun connect(
        scope: CoroutineScope,
        environment: KsrpcEnvironment<JniSerialized> = ksrpcEnvironment(JniSerialization()) {}
    ): JniConnection {
        val connection = JniConnection(scope, environment, createEnv())
        suspendCoroutine<Int> {
            registerService(connection, it.withConverter(newTypeConverter<Any?>().int))
        }
        return connection
    }
}
