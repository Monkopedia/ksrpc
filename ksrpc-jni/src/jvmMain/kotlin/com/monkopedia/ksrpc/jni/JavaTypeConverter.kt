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
package com.monkopedia.ksrpc.jni

import kotlinx.serialization.KSerializer

object JavaTypeConverter : JniTypeConverter<Any?> {

    init {
        JNIControl.ensureInit()
    }

    @Suppress("UNCHECKED_CAST")
    open class CastConverter<V> : Converter<Any?, V> {
        override fun convertTo(rawValue: Any?): V = rawValue as V
        override fun convertFrom(value: V): Any? = value
    }

    object BooleanConverter : CastConverter<Boolean>()
    object ByteConverter : CastConverter<Byte>()
    object ShortConverter : CastConverter<Short>()
    object IntConverter : CastConverter<Int>()
    object LongConverter : CastConverter<Long>()
    object FloatConverter : CastConverter<Float>()
    object DoubleConverter : CastConverter<Double>()
    object CharConverter : CastConverter<Char>()
    object StringConverter : CastConverter<String>()

    override val boolean: Converter<Any?, Boolean> = BooleanConverter
    override val byte: Converter<Any?, Byte> = ByteConverter
    override val short: Converter<Any?, Short> = ShortConverter
    override val int: Converter<Any?, Int> = IntConverter
    override val long: Converter<Any?, Long> = LongConverter
    override val float: Converter<Any?, Float> = FloatConverter
    override val double: Converter<Any?, Double> = DoubleConverter
    override val char: Converter<Any?, Char> = CharConverter
    override val string: Converter<Any?, String> = StringConverter
}

actual fun <V> JniSer.converterOf(serializer: KSerializer<V>): Converter<*, V> {
    JNIControl.ensureInit()
    return object : Converter<JniSerialized, V> {
        override fun convertTo(rawValue: JniSerialized?): V {
            @Suppress("UNCHECKED_CAST")
            return rawValue?.let { decodeFromJni(serializer, it) } as V
        }

        override fun convertFrom(value: V): JniSerialized {
            return encodeToJni(serializer, value)
        }
    }
}

actual fun <T> newTypeConverter(): JniTypeConverter<T> {
    @Suppress("UNCHECKED_CAST")
    return JavaTypeConverter as JniTypeConverter<T>
}
