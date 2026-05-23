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
import com.monkopedia.jni.JNI_VERSION_1_6
import com.monkopedia.jni.jint
import com.monkopedia.jni.jlong
import com.monkopedia.jni.jobject
import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.KsrpcEnvironmentBuilder
import com.monkopedia.ksrpc.annotation.KsrpcInternal
import com.monkopedia.ksrpc.channels.Connection
import com.monkopedia.ksrpc.ksrpcEnvironment
import kotlin.experimental.ExperimentalNativeApi
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.toLong
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import platform.posix.usleep

/**
 * The configuration of a ksrpc service hosted inside a Kotlin/Native shared
 * library, registered once at load time via [ksrpcNativeHost].
 *
 * The whole host API is typed on [JniSerialized] so that the serializer can
 * only ever be a [CallDataSerializer][com.monkopedia.ksrpc.CallDataSerializer]
 * of [JniSerialized] (i.e. [JniSerialization]) — a mismatched serializer is a
 * compile error rather than a runtime failure.
 */
private class NativeHostConfig(
    val configure: KsrpcEnvironmentBuilder<JniSerialized>.() -> Unit,
    val register: suspend (KsrpcEnvironment<JniSerialized>, Connection<JniSerialized>) -> Unit
)

private var hostConfig: NativeHostConfig? = null

/**
 * Registers this Kotlin/Native shared library as a ksrpc host so it can be
 * driven from the JVM via [com.monkopedia.ksrpc.jni.KsrpcNativeHost] in
 * `ksrpc-jni`.
 *
 * Call this from a single `JNI_OnLoad` export; ksrpc owns all of the underlying
 * JNI plumbing, so the consumer writes no other native glue:
 *
 * ```
 * @CName("JNI_OnLoad")
 * fun jniOnLoad(vm: CPointer<JavaVMVar>, reserved: COpaquePointer?): jint =
 *     ksrpcNativeHost { env, connection ->
 *         connection.registerDefault(MyServiceImpl(env))
 *     }
 * ```
 *
 * The [configure] block tweaks the shared [KsrpcEnvironment] (logger, error
 * listener, coroutine exception handler, ...). The serializer is pre-set to
 * [JniSerialization]; because everything is typed on [JniSerialized] it can only
 * be replaced by another `CallDataSerializer<JniSerialized>`.
 *
 * The [register] lambda runs every time the JVM opens a connection, on the
 * calling JVM thread (with a live `JNIEnv`), receiving the shared
 * [KsrpcEnvironment] and the freshly-opened [Connection] so it can register the
 * service(s) it hosts.
 *
 * Note: `JNI_OnLoad`'s `JavaVM*` / reserved arguments are not needed by ksrpc —
 * the dispatcher is JVM-thread-driven, so there is no native-initiated thread to
 * attach. They exist only because JNI mandates the
 * `jint JNI_OnLoad(JavaVM*, void*)` ABI. This function simply returns the JNI
 * version for the export to return.
 *
 * @return the JNI version (`JNI_VERSION_1_6`) for `JNI_OnLoad` to return.
 */
fun ksrpcNativeHost(
    configure: KsrpcEnvironmentBuilder<JniSerialized>.() -> Unit = {},
    register: suspend (KsrpcEnvironment<JniSerialized>, Connection<JniSerialized>) -> Unit
): jint {
    hostConfig = NativeHostConfig(configure, register)
    return JNI_VERSION_1_6.toInt()
}

@OptIn(ExperimentalForeignApi::class)
@CName("Java_com_monkopedia_ksrpc_jni_KsrpcNativeHost_createEnv")
fun ksrpcNativeHostCreateEnv(env: CPointer<JNIEnvVar>, clazz: jobject): jlong {
    initThread(env)
    try {
        val config = hostConfig
            ?: error(
                "ksrpcNativeHost(...) was not called from JNI_OnLoad; " +
                    "no host has been registered."
            )
        val ksrpcEnv = ksrpcEnvironment(JniSerialization(), config.configure)
        return StableRef.create(ksrpcEnv).asCPointer().toLong()
    } catch (t: Throwable) {
        t.printStackTrace()
        usleep(1000000u)
        return -1
    }
}

@OptIn(ExperimentalForeignApi::class)
@CName("Java_com_monkopedia_ksrpc_jni_KsrpcNativeHost_registerService")
fun ksrpcNativeHostRegisterService(
    env: CPointer<JNIEnvVar>,
    clazz: jobject,
    input: jobject,
    output: jobject
) {
    initThread(env)
    try {
        val connection = NativeConnection.convertTo(input)
        val javaContinuation = JavaJniContinuationConverter<Int>(env).convertTo(output)
        val config = hostConfig
            ?: error(
                "ksrpcNativeHost(...) was not called from JNI_OnLoad; " +
                    "no host has been registered."
            )
        GlobalScope.launch(JNIDispatcher) {
            runCatching {
                config.register(connection.env, connection)
                0
            }.let {
                javaContinuation.resumeWith(newTypeConverter<jobject>().int, it)
            }
        }
    } catch (t: Throwable) {
        t.printStackTrace()
        usleep(1000000u)
    }
}
