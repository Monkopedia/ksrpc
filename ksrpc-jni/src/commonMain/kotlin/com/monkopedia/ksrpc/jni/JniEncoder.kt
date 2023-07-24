package com.monkopedia.ksrpc.jni

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

@OptIn(ExperimentalSerializationApi::class)
data class JniEncoder<T> internal constructor(
    override val serializersModule: SerializersModule = EmptySerializersModule(),
    private val typeConverter: JniTypeConverter<T>
) : AbstractEncoder() {

    private var outputList = newList<T?>()
    private val lastStructurePoints = mutableListOf<Int>()

    val serialized: JniSerialized
        get() = outputList.asSerialized

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
        lastStructurePoints.add(outputList.size)
        outputList.add(typeConverter.convertInt(0))
        return super.beginStructure(descriptor)
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        val position = lastStructurePoints.removeLast()
        outputList[position] = typeConverter.convertInt(outputList.size)
        super.endStructure(descriptor)
    }

    override fun encodeBoolean(value: Boolean) {
        outputList.add(typeConverter.convertBoolean(value))
    }

    override fun encodeByte(value: Byte) {
        outputList.add(typeConverter.convertByte(value))
    }

    override fun encodeShort(value: Short) {
        outputList.add(typeConverter.convertShort(value))
    }

    override fun encodeInt(value: Int) {
        outputList.add(typeConverter.convertInt(value))
    }

    override fun encodeLong(value: Long) {
        outputList.add(typeConverter.convertLong(value))
    }

    override fun encodeFloat(value: Float) {
        outputList.add(typeConverter.convertFloat(value))
    }

    override fun encodeDouble(value: Double) {
        outputList.add(typeConverter.convertDouble(value))
    }

    override fun encodeChar(value: Char) {
        outputList.add(typeConverter.convertChar(value))
    }

    override fun encodeString(value: String) {
        outputList.add(typeConverter.convertString(value))
    }

    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) {
        outputList.add(typeConverter.convertInt(index))
    }

    override fun encodeNotNullMark() {
        outputList.add(typeConverter.convertBoolean(true))
    }

    override fun encodeNull() {
        outputList.add(null)
    }

    override fun encodeElement(descriptor: SerialDescriptor, index: Int): Boolean {
        encodeInt(index)
        return super.encodeElement(descriptor, index)
    }
}
