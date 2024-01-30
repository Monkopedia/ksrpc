/**
 * Copyright (C) 2024 Jason Monk <monkopedia@gmail.com>
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

import com.monkopedia.jni.jobject
import com.monkopedia.jnitest.JNI
import com.monkopedia.jnitest.threadEnv
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.serialization.KSerializer

@OptIn(ExperimentalForeignApi::class)
class NativeTypeConverter : JniTypeConverter<jobject?> {

    object BooleanConverter : Converter<jobject?, Boolean> {
        override fun convertTo(rawValue: jobject?): Boolean {
            return JNI.Bool.get(rawValue ?: error("convertBoolean"))
        }

        override fun convertFrom(value: Boolean): jobject? {
            return JNI.Bool.new(value)
        }
    }

    object ByteConverter : Converter<jobject?, Byte> {
        override fun convertTo(rawValue: jobject?): Byte {
            return JNI.Byte.get(rawValue ?: error("convertByte"))
        }

        override fun convertFrom(value: Byte): jobject? {
            return JNI.Byte.new(value)
        }
    }

    object ShortConverter : Converter<jobject?, Short> {

        override fun convertTo(rawValue: jobject?): Short {
            return JNI.Short.get(rawValue ?: error("convertShort"))
        }

        override fun convertFrom(value: Short): jobject? {
            return JNI.Short.new(value)
        }
    }

    object IntConverter : Converter<jobject?, Int> {

        override fun convertTo(rawValue: jobject?): Int {
            return JNI.Int.get(rawValue ?: error("convertInt"))
        }

        override fun convertFrom(value: Int): jobject? {
            return JNI.Int.new(value)
        }
    }

    object LongConverter : Converter<jobject?, Long> {

        override fun convertTo(rawValue: jobject?): Long {
            return JNI.Long.get(rawValue ?: error("convertLong"))
        }

        override fun convertFrom(value: Long): jobject? {
            return JNI.Long.new(value)
        }
    }

    object FloatConverter : Converter<jobject?, Float> {

        override fun convertTo(rawValue: jobject?): Float {
            return JNI.Float.get(rawValue ?: error("convertFloat"))
        }

        override fun convertFrom(value: Float): jobject? {
            return JNI.Float.new(value)
        }
    }

    object DoubleConverter : Converter<jobject?, Double> {

        override fun convertTo(rawValue: jobject?): Double {
            return JNI.Double.get(rawValue ?: error("convertDouble"))
        }

        override fun convertFrom(value: Double): jobject? {
            return JNI.Double.new(value)
        }
    }

    object CharConverter : Converter<jobject?, Char> {

        override fun convertTo(rawValue: jobject?): Char {
            return JNI.Char.get(rawValue ?: error("convertChar"))
        }

        override fun convertFrom(value: Char): jobject? {
            return JNI.Char.new(value)
        }
    }

    object StringConverter : Converter<jobject?, String> {

        override fun convertTo(rawValue: jobject?): String {
            return JNI.getString(rawValue ?: error("convertToString"))
                ?: error("convertToStringReturn")
        }

        override fun convertFrom(value: String): jobject? {
            return JNI.newString(value)
        }
    }

    override val boolean: Converter<jobject?, Boolean> = BooleanConverter
    override val byte: Converter<jobject?, Byte> = ByteConverter
    override val short: Converter<jobject?, Short> = ShortConverter
    override val int: Converter<jobject?, Int> = IntConverter
    override val long: Converter<jobject?, Long> = LongConverter
    override val float: Converter<jobject?, Float> = FloatConverter
    override val double: Converter<jobject?, Double> = DoubleConverter
    override val char: Converter<jobject?, Char> = CharConverter
    override val string: Converter<jobject?, String> = StringConverter
}

@OptIn(ExperimentalForeignApi::class)
actual fun <V> JniSer.converterOf(serializer: KSerializer<V>): Converter<*, V> {
    return object : Converter<jobject?, V> {
        override fun convertTo(rawValue: jobject?): V {
            @Suppress("UNCHECKED_CAST")
            return rawValue?.let {
                decodeFromJni(serializer, JniSerialized.fromJvm(threadEnv, it))
            } as V
        }

        override fun convertFrom(value: V): jobject? {
            return value?.let { encodeToJni(serializer, it).toJvm(threadEnv) }
        }
    }
}

actual fun <T> newTypeConverter(): JniTypeConverter<T> {
    @Suppress("UNCHECKED_CAST")
    return NativeTypeConverter() as JniTypeConverter<T>
}
