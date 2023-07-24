package com.monkopedia.ksrpc.jni

import com.monkopedia.jni.jobject
import com.monkopedia.jnitest.JNI
import kotlinx.cinterop.ExperimentalForeignApi

@OptIn(ExperimentalForeignApi::class)
class NativeTypeConverter : JniTypeConverter<jobject?> {

    override fun convertToBoolean(rawValue: jobject?): Boolean {
        return JNI.getBoolean(rawValue ?: error("convertBoolean"))
    }

    override fun convertToByte(rawValue: jobject?): Byte {
        return JNI.getByte(rawValue ?: error("convertByte"))
    }

    override fun convertToShort(rawValue: jobject?): Short {
        return JNI.getShort(rawValue ?: error("convertShort"))
    }

    override fun convertToInt(rawValue: jobject?): Int {
        return JNI.getInt(rawValue ?: error("convertInt"))
    }

    override fun convertToLong(rawValue: jobject?): Long {
        return JNI.getLong(rawValue ?: error("convertLong"))
    }

    override fun convertToFloat(rawValue: jobject?): Float {
        return JNI.getFloat(rawValue ?: error("convertFloat"))
    }

    override fun convertToDouble(rawValue: jobject?): Double {
        return JNI.getDouble(rawValue ?: error("convertDouble"))
    }

    override fun convertToChar(rawValue: jobject?): Char {
        return JNI.getChar(rawValue ?: error("convertChar"))
    }

    override fun convertToString(rawValue: jobject?): String {
        return JNI.getString(rawValue ?: error("convertToString")) ?: error("convertToStringReturn")
    }

    override fun convertBoolean(value: Boolean): jobject? {
        return JNI.newBoolean(value)
    }

    override fun convertByte(value: Byte): jobject? {
        return JNI.newByte(value)
    }

    override fun convertShort(value: Short): jobject? {
        return JNI.newShort(value)
    }

    override fun convertInt(value: Int): jobject? {
        return JNI.newInt(value)
    }

    override fun convertLong(value: Long): jobject? {
        return JNI.newLong(value)
    }

    override fun convertFloat(value: Float): jobject? {
        return JNI.newFloat(value)
    }

    override fun convertDouble(value: Double): jobject? {
        return JNI.newDouble(value)
    }

    override fun convertChar(value: Char): jobject? {
        return JNI.newChar(value)
    }

    override fun convertString(value: String): jobject? {
        return JNI.newString(value)
    }
}

actual fun <T> newTypeConverter(): JniTypeConverter<T> {
    @Suppress("UNCHECKED_CAST")
    return NativeTypeConverter() as JniTypeConverter<T>
}