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
 * The library must already be loaded (e.g. via
 * [NativeUtils.loadLibraryFromJar]). The consumer declares the native binding on
 * one of their own classes -- an `external fun` whose `@CName` is named after
 * that class -- and passes a reference to it into [connect]. The binding takes a
 * single [JniHostInit], which the consumer forwards to `ksrpcHostConnection`:
 *
 * ```
 * // The consumer's own JVM class declares the binding:
 * object MyNativeService {
 *     external fun initialize(host: JniHostInit)
 * }
 *
 * // ... and connects with it:
 * NativeUtils.loadLibraryFromJar("/libmyservice.so")
 * val connection = KsrpcNativeHost.connect(scope, MyNativeService::initialize)
 * val service = connection.defaultChannel().toStub<MyService, JniSerialized>()
 * ```
 *
 * The matching native side (`@CName("Java_..._MyNativeService_initialize")`)
 * just forwards the [JniHostInit] to
 * [ksrpcHostConnection][com.monkopedia.ksrpc.jni.ksrpcHostConnection].
 */
object KsrpcNativeHost {

    /**
     * Opens a [Connection] to the native host on the given [scope]: it builds the
     * connection, invokes [initialize] (the consumer's native binding) so the
     * host's `setup` lambda runs against *this* connection (building its own
     * per-connection native environment and service instance(s)), and returns it
     * ready to use. Each call is independent -- nothing is shared across
     * connections.
     *
     * [initialize] is the consumer's native entry point, usually a reference to an
     * `external fun` on one of their classes (e.g. `MyNativeService::initialize`)
     * that forwards its [JniHostInit] to
     * [ksrpcHostConnection][com.monkopedia.ksrpc.jni.ksrpcHostConnection]. The
     * [JniHostInit] is an opaque bundle of the per-connection JNI context; the
     * consumer never unpacks it.
     *
     * The connection uses [JniSerialization]; the optional [environment]
     * configures the JVM-side [KsrpcEnvironment] (logger, error listener, ...). The
     * native side builds its own per-connection environment, configured via the
     * `configure` block passed to `ksrpcHostConnection`.
     *
     * @sample com.monkopedia.ksrpc.samples.jniHostConnect
     */
    suspend fun connect(
        scope: CoroutineScope,
        initialize: (JniHostInit) -> Unit,
        environment: KsrpcEnvironment<JniSerialized> = ksrpcEnvironment(JniSerialization()) {}
    ): JniConnection {
        val connection = JniConnection(scope, environment)
        val handle = suspendCoroutine<Long> {
            initialize(
                JniHostInit(
                    connection,
                    scope.asNativeScope,
                    it.withConverter(newTypeConverter<Any?>().long)
                )
            )
        }
        connection.attachNative(handle)
        return connection
    }
}
