package com.monkopedia.ksrpc.jni

class JavaTypeConverter() : JniTypeConverter<Any?> {

    override fun convertBoolean(value: Boolean): Any = value

    override fun convertByte(value: Byte): Any = value

    override fun convertShort(value: Short): Any = value

    override fun convertInt(value: Int): Any = value

    override fun convertLong(value: Long): Any = value

    override fun convertFloat(value: Float): Any = value

    override fun convertDouble(value: Double): Any = value

    override fun convertChar(value: Char): Any = value

    override fun convertString(value: String): Any = value

    override fun convertToString(rawValue: Any?): String = rawValue as String

    override fun convertToChar(rawValue: Any?): Char = rawValue as Char

    override fun convertToDouble(rawValue: Any?): Double = rawValue as Double

    override fun convertToFloat(rawValue: Any?): Float = rawValue as Float

    override fun convertToLong(rawValue: Any?): Long = rawValue as Long

    override fun convertToInt(rawValue: Any?): Int = rawValue as Int

    override fun convertToShort(rawValue: Any?): Short = rawValue as Short

    override fun convertToByte(rawValue: Any?): Byte = rawValue as Byte

    override fun convertToBoolean(rawValue: Any?): Boolean = rawValue as Boolean
}

actual fun <T> newTypeConverter(): JniTypeConverter<T> {
    @Suppress("UNCHECKED_CAST")
    return JavaTypeConverter() as JniTypeConverter<T>
}