@file:OptIn(ExperimentalSerializationApi::class)

package com.monkopedia.ksrpc.jni

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

@OptIn(ExperimentalSerializationApi::class)
data class JniDecoder<T> internal constructor(
    override val serializersModule: SerializersModule = EmptySerializersModule(),
    private val typeConverter: JniTypeConverter<T>,
    private val inputList: BasicList<T>
) : AbstractDecoder() {
    private val structEnds = mutableListOf<Int>()
    private var index = 0

    private fun next() = inputList[index++]

    fun decoderFor(target: JniSerialized): JniDecoder<T> {
        @Suppress("UNCHECKED_CAST")
        return copy(inputList = target.list as BasicList<T>)
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        structEnds.add(typeConverter.convertToInt(next()))
        return super.beginStructure(descriptor)
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        structEnds.removeLast()
    }

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        return if (index < structEnds.last()) typeConverter.convertToInt(next()) else CompositeDecoder.DECODE_DONE
    }

    override fun decodeNotNullMark(): Boolean {
        return next()?.let { typeConverter.convertToBoolean(it) } ?: false
    }

    override fun decodeNull(): Nothing? {
        return null
    }

    override fun decodeBoolean(): Boolean {
        return typeConverter.convertToBoolean(next())
    }

    override fun decodeByte(): Byte {
        return typeConverter.convertToByte(next())
    }

    override fun decodeShort(): Short {
        return typeConverter.convertToShort(next())
    }

    override fun decodeInt(): Int {
        return typeConverter.convertToInt(next())
    }

    override fun decodeLong(): Long {
        return typeConverter.convertToLong(next())
    }

    override fun decodeFloat(): Float {
        return typeConverter.convertToFloat(next())
    }

    override fun decodeDouble(): Double {
        return typeConverter.convertToDouble(next())
    }

    override fun decodeChar(): Char {
        return typeConverter.convertToChar(next())
    }

    override fun decodeString(): String {
        return typeConverter.convertToString(next())
    }

    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int {
        return typeConverter.convertToInt(next())
    }
}
