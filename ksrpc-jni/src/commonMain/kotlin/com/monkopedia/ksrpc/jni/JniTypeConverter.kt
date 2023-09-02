package com.monkopedia.ksrpc.jni

import kotlinx.serialization.KSerializer

interface JniTypeConverter<T> {
    val boolean: Converter<T, Boolean>

    val byte: Converter<T, Byte>

    val short: Converter<T, Short>

    val int: Converter<T, Int>

    val long: Converter<T, Long>

    val float: Converter<T, Float>

    val double: Converter<T, Double>

    val char: Converter<T, Char>

    val string: Converter<T, String>
}

interface Converter<T, N> {
    fun convertTo(rawValue: T?): N
    fun convertFrom(value: N): T?
}

expect fun <V> JniSer.converterOf(serializer: KSerializer<V>): Converter<*, V>
expect fun <T> newTypeConverter(): JniTypeConverter<T>