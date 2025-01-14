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
@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.jnitest

import com.monkopedia.jni.JNIEnvVar
import com.monkopedia.jni.JNINativeInterface_
import com.monkopedia.jni.jclass
import com.monkopedia.jni.jmethodID
import com.monkopedia.jni.jobject
import com.monkopedia.jni.jstring
import com.monkopedia.jni.jvalue
import kotlin.native.concurrent.ThreadLocal
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVarOf
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.cValuesOf
import kotlinx.cinterop.cstr
import kotlinx.cinterop.get
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.wcstr

private typealias JniInvoker<R> = MemScope.(
    JNINativeInterface_,
    CPointer<JNIEnvVar>?,
    jobject?,
    jmethodID?,
    CPointer<jvalue>?,
    Boolean
) -> R

@ThreadLocal
internal lateinit var jni: JNINativeInterface_

@ThreadLocal
internal lateinit var env: CPointer<CPointerVarOf<CPointer<JNINativeInterface_>>>

val threadJni: JNINativeInterface_
    get() {
        return jni
    }
val threadEnv: CPointer<CPointerVarOf<CPointer<JNINativeInterface_>>>
    get() = env

fun initThread(e: CPointer<JNIEnvVar>) {
    JNI.init(e)
}

@OptIn(ExperimentalForeignApi::class, ExperimentalUnsignedTypes::class)
internal object JNI {

    val boolArg: jvalue.(Boolean) -> Unit = {
        this.z = if (it) 1u else 0u
    }
    val byteArg: jvalue.(kotlin.Byte) -> Unit = {
        this.b = it
    }
    val shortArg: jvalue.(kotlin.Short) -> Unit = {
        this.s = it
    }
    val intArg: jvalue.(kotlin.Int) -> Unit = {
        this.i = it
    }
    val longArg: jvalue.(kotlin.Long) -> Unit = {
        this.j = it
    }
    val floatArg: jvalue.(kotlin.Float) -> Unit = {
        this.f = it
    }
    val doubleArg: jvalue.(kotlin.Double) -> Unit = {
        this.d = it
    }
    val charArg: jvalue.(kotlin.Char) -> Unit = {
        this.c = it.code.toUShort()
    }
    val objArg: jvalue.(jobject?) -> Unit = {
        this.l = it
    }
    val voidMethod: JniInvoker<Unit> = { jni, env, jobj, jmeth, arg, static ->
        if (static) {
            jni.CallStaticVoidMethodA!!(env, jobj, jmeth, arg)
        } else {
            jni.CallVoidMethodA!!(env, jobj, jmeth, arg)
        }
    }
    val boolMethod: JniInvoker<Boolean> = { jni, env, jobj, jmeth, arg, static ->
        if (static) {
            jni.CallStaticBooleanMethodA!!(env, jobj, jmeth, arg) != 0u.toUByte()
        } else {
            jni.CallBooleanMethodA!!(env, jobj, jmeth, arg) != 0u.toUByte()
        }
    }
    val byteMethod: JniInvoker<kotlin.Byte> = { jni, env, jobj, jmeth, arg, static ->
        if (static) {
            jni.CallStaticByteMethodA!!(env, jobj, jmeth, arg)
        } else {
            jni.CallByteMethodA!!(env, jobj, jmeth, arg)
        }
    }
    val shortMethod: JniInvoker<kotlin.Short> = { jni, env, jobj, jmeth, arg, static ->
        if (static) {
            jni.CallStaticShortMethodA!!(env, jobj, jmeth, arg)
        } else {
            jni.CallShortMethodA!!(env, jobj, jmeth, arg)
        }
    }
    val intMethod: JniInvoker<kotlin.Int> = { jni, env, jobj, jmeth, arg, static ->
        if (static) {
            jni.CallStaticIntMethodA!!(env, jobj, jmeth, arg)
        } else {
            jni.CallIntMethodA!!(env, jobj, jmeth, arg)
        }
    }
    val longMethod: JniInvoker<kotlin.Long> = { jni, env, jobj, jmeth, arg, static ->
        if (static) {
            jni.CallStaticLongMethodA!!(env, jobj, jmeth, arg)
        } else {
            jni.CallLongMethodA!!(env, jobj, jmeth, arg)
        }
    }
    val floatMethod: JniInvoker<kotlin.Float> = { jni, env, jobj, jmeth, arg, static ->
        if (static) {
            jni.CallStaticFloatMethodA!!(env, jobj, jmeth, arg)
        } else {
            jni.CallFloatMethodA!!(env, jobj, jmeth, arg)
        }
    }
    val doubleMethod: JniInvoker<kotlin.Double> = { jni, env, jobj, jmeth, arg, static ->
        if (static) {
            jni.CallStaticDoubleMethodA!!(env, jobj, jmeth, arg)
        } else {
            jni.CallDoubleMethodA!!(env, jobj, jmeth, arg)
        }
    }
    val charMethod: JniInvoker<kotlin.Char> = { jni, env, jobj, jmeth, arg, static ->
        if (static) {
            jni.CallStaticCharMethodA!!(env, jobj, jmeth, arg).toInt().toChar()
        } else {
            jni.CallCharMethodA!!(env, jobj, jmeth, arg).toInt().toChar()
        }
    }
    val objMethod: JniInvoker<jobject?> = { jni, env, jobj, jmeth, arg, static ->
        if (static) {
            jni.CallStaticObjectMethodA!!(env, jobj, jmeth, arg)
        } else {
            jni.CallObjectMethodA!!(env, jobj, jmeth, arg)
        }
    }
    val constructor: JniInvoker<jobject?> = { jni, env, jobj, jmeth, arg, static ->
        jni.NewObjectA!!(env, jobj, jmeth, arg)
    }

    val stringMethod: JniInvoker<String?> = { jni, env, jobj, jmeth, arg, static ->
        val str = if (static) {
            jni.CallStaticObjectMethodA!!(env, jobj, jmeth, arg)
        } else {
            jni.CallObjectMethodA!!(env, jobj, jmeth, arg)
        }
        val utfChars = jni.GetStringUTFChars!!(env, str, cValuesOf(0u.toUByte()).ptr)
        utfChars?.toKString()?.also {
            jni.ReleaseStringUTFChars!!(env, str, utfChars)
        }
    }

    fun <V, R> ((jobject) -> V).mapped(map: (V) -> R): (jobject) -> R =
        { map(this@mapped.invoke(it)) }

    open class JvmClass(private val str: String) {
        val cls get() = findClass(str)

        inner class Method0<R>(
            name: String,
            sig: String,
            private val invoker: JniInvoker<R>
        ) : (jobject) -> R {
            val method by lazy { cls.findMethod(name, sig) }

            override fun invoke(obj: jobject): R = memScoped {
                invoker(jni, env, obj, method, null, false)
            }
        }

        inner class Method1<I, R>(
            name: String,
            sig: String,
            private val typeMapping: jvalue.(I) -> Unit,
            private val invoker: JniInvoker<R>
        ) : (jobject, I) -> R {
            val method by lazy { cls.findMethod(name, sig) }

            override fun invoke(obj: jobject, input: I): R = memScoped {
                invoker(jni, env, obj, method, alloc<jvalue> { this.typeMapping(input) }.ptr, false)
            }
        }

        inner class Method2<I, J, R>(
            name: String,
            sig: String,
            private val typeMapping1: jvalue.(I) -> Unit,
            private val typeMapping2: jvalue.(J) -> Unit,
            private val invoker: JniInvoker<R>
        ) : (jobject, I, J) -> R {
            val method by lazy { cls.findMethod(name, sig) }

            override fun invoke(obj: jobject, input1: I, input2: J): R = memScoped {
                invoker(
                    jni,
                    env,
                    obj,
                    method,
                    allocArray<jvalue>(2) { i: kotlin.Int ->
                        if (i == 0) {
                            typeMapping1(input1)
                        } else {
                            typeMapping2(input2)
                        }
                    },
                    false
                )
            }
        }

        inner class Constructor<I, R>(
            private val name: String,
            private val sig: String,
            private val typeMapping: jvalue.(I) -> Unit,
            private val invoker: JniInvoker<R>
        ) : (I) -> R {
            val method get() = cls.findMethod(name, sig)

            override fun invoke(input: I): R = memScoped {
                invoker(jni, env, cls, method, alloc<jvalue> { this.typeMapping(input) }.ptr, false)
            }
        }

        inner class Constructor0<R>(
            name: String,
            sig: String,
            private val invoker: JniInvoker<R>
        ) : () -> R {
            val method by lazy { cls.findMethod(name, sig) }

            override fun invoke(): R = memScoped {
                invoker(jni, env, cls, method, null, false)
            }
        }

        inner class StaticMethod<I, R>(
            private val name: String,
            private val sig: String,
            private val typeMapping: jvalue.(I) -> Unit,
            private val invoker: JniInvoker<R>
        ) : (I) -> R {
            val method by lazy { cls.findStaticMethod(name, sig) }

            override fun invoke(input: I): R = memScoped {
                invoker(jni, env, cls, method, alloc<jvalue> { this.typeMapping(input) }.ptr, true)
            }
        }
    }

    data object Bool : JvmClass("java/lang/Boolean") {
        val new = Constructor("<init>", "(Z)V", boolArg, constructor)
        val get = Method0("booleanValue", "()Z", boolMethod)
    }

    data object Byte : JvmClass("java/lang/Byte") {
        val new = Constructor("<init>", "(B)V", byteArg, constructor)
        val get = Method0("byteValue", "()B", byteMethod)
    }

    data object Short : JvmClass("java/lang/Short") {
        val new = Constructor("<init>", "(S)V", shortArg, constructor)
        val get = Method0("shortValue", "()S", shortMethod)
    }

    data object Int : JvmClass("java/lang/Integer") {
        val new = Constructor("<init>", "(I)V", intArg, constructor)
        val get = Method0("intValue", "()I", intMethod)
    }

    data object Long : JvmClass("java/lang/Long") {
        val new = Constructor("<init>", "(J)V", longArg, constructor)
        val get = Method0("longValue", "()J", longMethod)
    }

    data object Float : JvmClass("java/lang/Float") {
        val new = Constructor("<init>", "(F)V", floatArg, constructor)
        val get = Method0("floatValue", "()F", floatMethod)
    }

    data object Double : JvmClass("java/lang/Double") {
        val new = Constructor("<init>", "(D)V", doubleArg, constructor)
        val get = Method0("doubleValue", "()D", doubleMethod)
    }

    data object Char : JvmClass("java/lang/Character") {
        val new = Constructor("<init>", "(C)V", charArg, constructor)
        val get = Method0("charValue", "()C", charMethod)
    }

    data object List : JvmClass("java/util/List") {
        val get = Method1("get", "(I)Ljava/lang/Object;", intArg, objMethod)
        val size = Method0("size", "()I", intMethod)
    }

    data object JavaJniContinuation : JvmClass("com/monkopedia/ksrpc/jni/JavaJniContinuation") {
        val resumeSuccess = Method1("resumeSuccess", "(Ljava/lang/Object;)V", objArg, voidMethod)
        val resumeFailure = Method1("resumeFailure", "(Ljava/lang/Object;)V", objArg, voidMethod)
    }

    data object ArrayList : JvmClass("java/util/ArrayList") {
        val new = Constructor0("<init>", "()V", constructor)
        val add = Method1("add", "(Ljava/lang/Object;)Z", objArg, voidMethod)
        val set =
            Method2("set", "(ILjava/lang/Object;)Ljava/lang/Object;", intArg, objArg, objMethod)
    }

    data object Obj : JvmClass("java/lang/Object") {
        val toString = Method0("toString", "()Ljava/lang/String;", stringMethod)
    }

    data object JavaListWrapperKt : JvmClass("com/monkopedia/ksrpc/jni/JavaListWrapperKt") {
        val toSerialized = StaticMethod(
            "toSerialized",
            "(Ljava/util/List;)Lcom/monkopedia/ksrpc/jni/JniSerialized;",
            objArg,
            objMethod
        )
        val toList = StaticMethod(
            "toList",
            "(Lcom/monkopedia/ksrpc/jni/JniSerialized;)Ljava/util/List;",
            objArg,
            objMethod
        )
    }

    data object NativeJniContinuation : JvmClass("com/monkopedia/ksrpc/jni/NativeJniContinuation") {
        val new = Constructor("<init>", "(J)V", longArg, constructor)
        val getNativeObject = Method0("getNativeObject", "()J", longMethod)
    }

    data object JniConnection : JvmClass("com/monkopedia/ksrpc/jni/JniConnection") {
        val getNativeConnection = Method0(
            "getNativeConnection",
            "()J",
            longMethod
        )
        val closeFromNative = Method1(
            "closeFromNative",
            "(Lcom/monkopedia/ksrpc/jni/NativeJniContinuation;)V",
            objArg,
            voidMethod
        )
        val sendFromNative = Method2(
            "sendFromNative",
            "(Lcom/monkopedia/ksrpc/jni/JniSerialized;" +
                "Lcom/monkopedia/ksrpc/jni/NativeJniContinuation;)V",
            objArg,
            objArg,
            voidMethod
        )
    }

    fun init(e: CPointer<JNIEnvVar>) {
        env = e
        jni = e[0]!![0]
    }

    fun newString(str: String): jstring? = memScoped {
        jni.NewString!!.invoke(env, str.wcstr.ptr, str.length)
    }

    fun getString(str: jstring): String? = memScoped {
        jni.GetStringUTFChars!!.invoke(env, str, cValuesOf(0u.toUByte()).ptr)?.toKString()
    }

    private fun jclass.findMethod(name: String, signature: String) = memScoped {
        jni.GetMethodID!!.invoke(env, this@findMethod, name.cstr.ptr, signature.cstr.ptr)
            ?: error("Missing method $name $signature")
    }

    private fun jclass.findStaticMethod(name: String, signature: String) = memScoped {
        jni.GetStaticMethodID!!.invoke(
            env,
            this@findStaticMethod,
            name.cstr.ptr,
            signature.cstr.ptr
        ) ?: error("Missing method $name $signature")
    }

    private fun findClass(str: String) = memScoped {
        jni.FindClass!!.invoke(env, str.cstr.ptr) ?: error("Missing class $str")
    }
}
