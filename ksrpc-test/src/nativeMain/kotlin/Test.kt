/**
 * Copyright (C) 2025 Jason Monk <monkopedia@gmail.com>
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

import com.monkopedia.jni.JNIEnvVar
import com.monkopedia.jni.JNINativeInterface_
import com.monkopedia.jni.jmethodID
import com.monkopedia.jni.jobject
import com.monkopedia.jni.jvalue
import com.monkopedia.jnitest.com.monkopedia.ksrpc.jni.JNIDispatcher
import com.monkopedia.jnitest.com.monkopedia.ksrpc.jni.JavaJniContinuationConverter
import com.monkopedia.jnitest.initThread
import com.monkopedia.jnitest.threadEnv
import com.monkopedia.jnitest.threadJni
import com.monkopedia.ksrpc.JniTestInterface
import com.monkopedia.ksrpc.TestJniImpl
import com.monkopedia.ksrpc.channels.registerDefault
import com.monkopedia.ksrpc.jni.JniSer
import com.monkopedia.ksrpc.jni.JniSerialization
import com.monkopedia.ksrpc.jni.JniSerialized
import com.monkopedia.ksrpc.jni.NativeConnection
import com.monkopedia.ksrpc.jni.NativeJniContinuation
import com.monkopedia.ksrpc.jni.NativeJniContinuationConverter
import com.monkopedia.ksrpc.jni.decodeFromJni
import com.monkopedia.ksrpc.jni.encodeToJni
import com.monkopedia.ksrpc.jni.fromJvm
import com.monkopedia.ksrpc.jni.newTypeConverter
import com.monkopedia.ksrpc.jni.toJvm
import com.monkopedia.ksrpc.jni.withConverter
import com.monkopedia.ksrpc.ksrpcEnvironment
import com.monkopedia.ksrpc.serialized
import kotlin.coroutines.Continuation
import kotlin.coroutines.suspendCoroutine
import kotlin.experimental.ExperimentalNativeApi
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.cValue
import kotlinx.cinterop.cstr
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toLong
import kotlinx.cinterop.wcstr
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import platform.posix.usleep

@OptIn(ExperimentalForeignApi::class)
@CName("Java_com_monkopedia_ksrpc_NativeHost_serializeDeserialize")
fun serializeDeserialize(env: CPointer<JNIEnvVar>, clazz: jobject, input: jobject): jobject? {
    initThread(env)
    try {
        val jniSerialized = JniSerialized.fromJvm(threadEnv, input)
        val obj = JniSer.decodeFromJni<ComplexClass>(jniSerialized)
        val output = obj.copy(intValue = (obj.intValue ?: 0) + 1)
        return JniSer.encodeToJni(output).toJvm(threadEnv)
    } catch (t: Throwable) {
        t.printStackTrace()
        usleep(1000000u)
        return null
    }
}

@OptIn(ExperimentalForeignApi::class)
@CName("Java_com_monkopedia_ksrpc_NativeHost_createContinuations")
fun createContinuations(env: CPointer<JNIEnvVar>, clazz: jobject, input: jobject, list: jobject) {
    initThread(env)
    try {
        memScoped {
            val ref = threadJni.NewGlobalRef!!(threadEnv, input)
            val receiver: suspend (String) -> Unit = createReceiver(ref!!)
            val jni = threadJni
            val listClass = jni.FindClass!!.invoke(env, "java/util/List".cstr.ptr)
            val addMethod =
                jni.GetMethodID!!.invoke(
                    env,
                    listClass,
                    "add".cstr.ptr,
                    "(Ljava/lang/Object;)Z".cstr.ptr
                )
            val context = newSingleThreadContext("context")
            val scope = CoroutineScope(context)
            val continuation = createContinuation(scope) {
                receiver("Result: $it")
            }
            val completion = CompletableDeferred<Int>()
            val chained1 = createContinuation(scope) {
                val firstValue = completion.await()
                receiver("$it + $firstValue = ${it + firstValue}")
                println("Delete ref")
            }
            val chained2 = createContinuation(scope) {
                completion.complete(it)
            }
            add(jni, env, list, addMethod, continuation)
            add(jni, env, list, addMethod, chained1)
            add(jni, env, list, addMethod, chained2)
        }
    } catch (t: Throwable) {
        t.printStackTrace()
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun MemScope.add(
    jni: JNINativeInterface_,
    env: CPointer<JNIEnvVar>,
    list: jobject,
    addMethod: jmethodID?,
    continuation: Continuation<Int>
) {
    val converter = NativeJniContinuationConverter<Int>(env)
    jni.CallBooleanMethodA!!.invoke(
        env,
        list,
        addMethod,
        cValue<jvalue> {
            this.l =
                converter.convertFrom(
                    NativeJniContinuation(continuation, newTypeConverter<jobject?>().int)
                )
        }.ptr
    )
}

private fun createContinuation(
    scope: CoroutineScope,
    resultHandler: suspend (Int) -> Unit
): Continuation<Int> {
    val result = CompletableDeferred<Continuation<Int>>()
    scope.launch(JNIDispatcher) {
        val v = suspendCoroutine {
            result.complete(it)
        }
        resultHandler(v)
    }
    while (!result.isCompleted) {
        usleep(100u)
    }
    return result.getCompleted()
}

@OptIn(ExperimentalForeignApi::class)
fun createReceiver(input: jobject): suspend (String) -> Unit {
    val method = memScoped {
        val cls = threadJni.FindClass!!.invoke(threadEnv, "com/monkopedia/ksrpc/Receiver".cstr.ptr)
        val messagePtr = "message".cstr.ptr
        val sig = "(Ljava/lang/String;)V".cstr.ptr
        threadJni.GetMethodID!!.invoke(threadEnv, cls, messagePtr, sig)
    }
    return { v: String ->
        memScoped {
            val str = threadJni.NewString!!.invoke(threadEnv, v.wcstr.ptr, v.length)
            threadJni.CallVoidMethodA!!.invoke(
                threadEnv,
                input,
                method,
                cValue<jvalue> {
                    this.l = str
                }.ptr
            )
            threadJni.DeleteLocalRef!!.invoke(threadEnv, str)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
@CName("Java_com_monkopedia_ksrpc_NativeHost_createContinuationRelay")
fun createContinuationRelay(env: CPointer<JNIEnvVar>, clazz: jobject, output: jobject): jobject? {
    initThread(env)
    val javaContinuation = JavaJniContinuationConverter<Int>(env).convertTo(output)
    val typeConverter = newTypeConverter<jobject>()
    val continuation = createContinuation(GlobalScope) {
        javaContinuation.resumeWith(typeConverter.int, Result.success(it + 1))
    }
    return NativeJniContinuationConverter<Int>(env).convertFrom(
        continuation.withConverter(typeConverter.int)
    )
}

@OptIn(ExperimentalForeignApi::class)
@CName("Java_com_monkopedia_ksrpc_NativeHost_registerService")
fun registerService(env: CPointer<JNIEnvVar>, clazz: jobject, input: jobject, output: jobject) {
    initThread(env)
    try {
        val jniSerialized = NativeConnection.convertTo(input)
        val javaContinuation = JavaJniContinuationConverter<Int>(env).convertTo(output)
        GlobalScope.launch(JNIDispatcher) {
            runCatching {
                val service: JniTestInterface = TestJniImpl()
                jniSerialized.registerDefault(service.serialized(jniSerialized.env))
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

@OptIn(ExperimentalForeignApi::class)
@CName("Java_com_monkopedia_ksrpc_NativeHost_createEnv")
fun createEnv(env: CPointer<JNIEnvVar>, clazz: jobject): Long {
    initThread(env)
    try {
        val env = ksrpcEnvironment(JniSerialization()) {}
        return StableRef.create(env).asCPointer().toLong()
    } catch (t: Throwable) {
        t.printStackTrace()
        usleep(1000000u)
        return -1
    }
}
