package com.monkopedia.ksrpc.jni

interface JniTypeConverter<T> {

    fun convertToBoolean(rawValue: T?): Boolean
    fun convertBoolean(value: Boolean): T

    fun convertToByte(rawValue: T?): Byte
    fun convertByte(value: Byte): T

    fun convertToShort(rawValue: T?): Short
    fun convertShort(value: Short): T

    fun convertToInt(rawValue: T?): Int
    fun convertInt(value: Int): T

    fun convertToLong(rawValue: T?): Long
    fun convertLong(value: Long): T

    fun convertToFloat(rawValue: T?): Float
    fun convertFloat(value: Float): T

    fun convertToDouble(rawValue: T?): Double
    fun convertDouble(value: Double): T

    fun convertToChar(rawValue: T?): Char
    fun convertChar(value: Char): T

    fun convertToString(rawValue: T?): String
    fun convertString(value: String): T
}

expect fun <T> newTypeConverter(): JniTypeConverter<T>