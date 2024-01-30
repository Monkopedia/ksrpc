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
