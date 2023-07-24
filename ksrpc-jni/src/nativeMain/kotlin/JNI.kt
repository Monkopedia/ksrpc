package com.monkopedia.jnitest

import com.monkopedia.jni.JNIEnvVar
import com.monkopedia.jni.JNINativeInterface_
import com.monkopedia.jni.jclass
import com.monkopedia.jni.jobject
import com.monkopedia.jni.jstring
import com.monkopedia.jni.jvalue
import com.monkopedia.ksrpc.jni.JniSerialized
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVarOf
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.cValue
import kotlinx.cinterop.cValuesOf
import kotlinx.cinterop.cstr
import kotlinx.cinterop.get
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.wcstr
import platform.posix.va_list

@OptIn(ExperimentalForeignApi::class, ExperimentalUnsignedTypes::class)
internal object JNI {

    // Permanent, for global JNI
    private lateinit var jni: JNINativeInterface_
    private lateinit var env: CPointer<CPointerVarOf<CPointer<JNINativeInterface_>>>
    private val boolClass by lazy {
        findClass("java/lang/Boolean")
    }
    private val byteClass by lazy {
        findClass("java/lang/Byte")
    }
    private val shortClass by lazy {
        findClass("java/lang/Short")
    }
    private val intClass by lazy {
        findClass("java/lang/Integer")
    }
    private val longClass by lazy {
        findClass("java/lang/Long")
    }
    private val floatClass by lazy {
        findClass("java/lang/Float")
    }
    private val doubleClass by lazy {
        findClass("java/lang/Double")
    }
    private val charClass by lazy {
        findClass("java/lang/Character")
    }
    private val listClass by lazy {
        findClass("java/util/List")
    }
    private val arrayListClass by lazy {
        findClass("java/util/ArrayList")
    }
    private val obj by lazy {
        findClass("java/lang/Object")
    }
    private val javaListWrapperKt by lazy {
        findClass("com/monkopedia/ksrpc/jni/JavaListWrapperKt")
    }
    private val newBool by lazy {
        boolClass.findMethod("<init>", "(Z)V")
    }
    private val newByte by lazy {
        byteClass.findMethod("<init>", "(B)V")
    }
    private val newShort by lazy {
        shortClass.findMethod("<init>", "(S)V")
    }
    private val newInt by lazy {
        intClass.findMethod("<init>", "(I)V")
    }
    private val newLong by lazy {
        longClass.findMethod("<init>", "(J)V")
    }
    private val newFloat by lazy {
        floatClass.findMethod("<init>", "(F)V")
    }
    private val newDouble by lazy {
        doubleClass.findMethod("<init>", "(D)V")
    }
    private val newChar by lazy {
        charClass.findMethod("<init>", "(C)V")
    }
    private val newArrayList by lazy {
        arrayListClass.findMethod("<init>", "()V")
    }
    private val valueBool by lazy {
        boolClass.findMethod("booleanValue", "()Z")
    }
    private val valueByte by lazy {
        byteClass.findMethod("byteValue", "()B")
    }
    private val valueShort by lazy {
        shortClass.findMethod("shortValue", "()S")
    }
    private val valueInt by lazy {
        intClass.findMethod("intValue", "()I")
    }
    private val valueLong by lazy {
        longClass.findMethod("longValue", "()J")
    }
    private val valueFloat by lazy {
        floatClass.findMethod("floatValue", "()F")
    }
    private val valueDouble by lazy {
        doubleClass.findMethod("doubleValue", "()D")
    }
    private val valueChar by lazy {
        charClass.findMethod("charValue", "()C")
    }
    private val get by lazy {
        listClass.findMethod("get", "(I)Ljava/lang/Object;")
    }
    private val size by lazy {
        listClass.findMethod("size", "()I")
    }
    private val add by lazy {
        arrayListClass.findMethod("add", "(Ljava/lang/Object;)Z")
    }
    private val set by lazy {
        arrayListClass.findMethod("set", "(ILjava/lang/Object;)Ljava/lang/Object;")
    }
    private val toString by lazy {
        obj.findMethod("toString", "()Ljava/lang/String;")
    }
    private val toSerialized by lazy {
        javaListWrapperKt.findStaticMethod("toSerialized", "(Ljava/util/List;)Lcom/monkopedia/ksrpc/jni/JniSerialized;")
    }
    private val toList by lazy {
        javaListWrapperKt.findStaticMethod("toList", "(Lcom/monkopedia/ksrpc/jni/JniSerialized;)Ljava/util/List;")
    }

    fun init(env: CPointer<JNIEnvVar>) {
        this.env = env
        this.jni = env[0]!![0]
    }

    fun newString(str: String): jstring? = memScoped {
        jni.NewString!!.invoke(env, str.wcstr.ptr, str.length)
    }

    fun newBoolean(b: Boolean) = memScoped {
        jni.NewObjectA!!.invoke(
            env,
            boolClass,
            newBool,
            alloc<jvalue> {
                this.z = if (b) 1u else 0u
            }.ptr
        )
    }

    fun newByte(b: Byte) = memScoped {
        jni.NewObjectA!!.invoke(
            env,
            byteClass,
            newByte,
            alloc<jvalue> {
                this.b = b
            }.ptr
        )
    }

    fun newShort(s: Short) = memScoped {
        jni.NewObjectA!!.invoke(
            env,
            shortClass,
            newShort,
            alloc<jvalue> {
                this.s = s
            }.ptr
        )
    }

    fun newInt(i: Int) = memScoped {
        jni.NewObjectA!!.invoke(
            env,
            intClass,
            newInt,
            alloc<jvalue> {
                this.i = i
            }.ptr
        )
    }

    fun newLong(l: Long) = memScoped {
        jni.NewObjectA!!.invoke(
            env,
            longClass,
            newLong,
            alloc<jvalue> {
                this.j = l
            }.ptr
        )
    }

    fun newFloat(f: Float) = memScoped {
        jni.NewObjectA!!.invoke(
            env,
            floatClass,
            newFloat,
            alloc<jvalue> {
                this.f = f
            }.ptr
        )
    }

    fun newDouble(d: Double) = memScoped {
        jni.NewObjectA!!.invoke(
            env,
            doubleClass,
            newDouble,
            alloc<jvalue> {
                this.d = d
            }.ptr
        )
    }

    fun newChar(c: Char) = memScoped {
        jni.NewObjectA!!.invoke(
            env,
            charClass,
            newChar,
            alloc<jvalue> {
                this.c = c.code.toUShort()
            }.ptr
        )
    }

    fun newList() = memScoped {
        jni.NewObjectA!!.invoke(
            env,
            arrayListClass,
            newArrayList,
            null
        )
    }

    fun getString(str: jstring): String? = memScoped {
        jni.GetStringUTFChars!!.invoke(env, str, cValuesOf(0u.toUByte()).ptr)?.toKString()
    }

    fun getBoolean(b: jobject): Boolean = memScoped {
        jni.CallBooleanMethodA!!.invoke(
            env,
            b,
            valueBool,
            null
        ) != 0u.toUByte()
    }

    fun getByte(b: jobject): Byte = memScoped {
        jni.CallByteMethodA!!.invoke(
            env,
            b,
            valueByte,
            null
        )
    }

    fun getShort(s: jobject): Short = memScoped {
        jni.CallShortMethodA!!.invoke(
            env,
            s,
            valueShort,
            null
        )
    }

    fun getInt(i: jobject): Int = memScoped {
        jni.CallIntMethodA!!.invoke(
            env,
            i,
            valueInt,
            null
        )
    }

    fun getLong(l: jobject): Long = memScoped {
        jni.CallLongMethodA!!.invoke(
            env,
            l,
            valueLong,
            null
        )
    }

    fun getFloat(f: jobject): Float = memScoped {
        jni.CallFloatMethodA!!.invoke(
            env,
            f,
            valueFloat,
            null
        )
    }

    fun getDouble(d: jobject): Double = memScoped {
        jni.CallDoubleMethodA!!.invoke(
            env,
            d,
            valueDouble,
            null
        )
    }

    fun getChar(c: jobject): Char = memScoped {
        jni.CallCharMethodA!!.invoke(
            env,
            c,
            valueChar,
            null
        ).toInt().toChar()
    }

    fun getFromList(list: jobject, index: Int) = memScoped {
        jni.CallObjectMethodA!!.invoke(
            env,
            list,
            get,
            alloc<jvalue> {
                this.i = index
            }.ptr
        )
    }

    fun sizeOfList(list: jobject) = memScoped {
        jni.CallIntMethodA!!.invoke(
            env,
            list,
            size,
            null
        )
    }

    fun addToList(list: jobject, item: jobject?) = memScoped {
        jni.CallBooleanMethodA!!.invoke(
            env,
            list,
            add,
            alloc<jvalue> {
                this.l = item
            }.ptr
        )
    }

    fun setInList(list: jobject, index: Int, item: jobject?) = memScoped {
        jni.CallObjectMethodA!!.invoke(
            env,
            list,
            set,
            allocArray<jvalue>(2) { i: Int ->
                if (i == 0) {
                    this.i = index
                } else {
                    this.l = item
                }
            }
        )
    }

    fun toList(serialized: jobject): jobject = memScoped {
        jni.CallStaticObjectMethodA!!.invoke(
            env,
            javaListWrapperKt,
            toList,
            cValue<jvalue> {
                l = serialized
            }.ptr
        ) ?: error("No list found for serializable")
    }

    fun toSerialized(list: jobject): jobject = memScoped {
        jni.CallStaticObjectMethodA!!.invoke(
            env,
            javaListWrapperKt,
            toSerialized,
            cValue<jvalue> {
                l = list
            }.ptr
        ) ?: error("No list found for serializable")
    }

    fun jobject.toStr() = memScoped {
        val jstr = jni.CallObjectMethodA!!.invoke(env, this@toStr, toString, null) as jstring
        jni.GetStringUTFChars!!.invoke(env, jstr, cValuesOf(0u.toUByte()).ptr)?.toKString()
    }

    private fun jclass.findMethod(name: String, signature: String) = memScoped {
        jni.GetMethodID!!.invoke(env, this@findMethod, name.cstr.ptr, signature.cstr.ptr) ?: error("Missing method $name $signature")
    }

    private fun jclass.findStaticMethod(name: String, signature: String) = memScoped {
        jni.GetStaticMethodID!!.invoke(env, this@findStaticMethod, name.cstr.ptr, signature.cstr.ptr) ?: error("Missing method $name $signature")
    }

    private fun findClass(str: String) = memScoped {
        jni.FindClass!!.invoke(env, str.cstr.ptr) ?: error("Missing class $str")
    }
}
