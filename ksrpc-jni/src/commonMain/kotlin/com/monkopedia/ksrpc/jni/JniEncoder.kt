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
        outputList.add(typeConverter.int.convertFrom(0))
        return super.beginStructure(descriptor)
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        val position = lastStructurePoints.removeLast()
        outputList[position] = typeConverter.int.convertFrom(outputList.size)
        super.endStructure(descriptor)
    }

    override fun encodeBoolean(value: Boolean) {
        outputList.add(typeConverter.boolean.convertFrom(value))
    }

    override fun encodeByte(value: Byte) {
        outputList.add(typeConverter.byte.convertFrom(value))
    }

    override fun encodeShort(value: Short) {
        outputList.add(typeConverter.short.convertFrom(value))
    }

    override fun encodeInt(value: Int) {
        outputList.add(typeConverter.int.convertFrom(value))
    }

    override fun encodeLong(value: Long) {
        outputList.add(typeConverter.long.convertFrom(value))
    }

    override fun encodeFloat(value: Float) {
        outputList.add(typeConverter.float.convertFrom(value))
    }

    override fun encodeDouble(value: Double) {
        outputList.add(typeConverter.double.convertFrom(value))
    }

    override fun encodeChar(value: Char) {
        outputList.add(typeConverter.char.convertFrom(value))
    }

    override fun encodeString(value: String) {
        outputList.add(typeConverter.string.convertFrom(value))
    }

    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) {
        outputList.add(typeConverter.int.convertFrom(index))
    }

    override fun encodeNotNullMark() {
        outputList.add(typeConverter.boolean.convertFrom(true))
    }

    override fun encodeNull() {
        outputList.add(null)
    }

    override fun encodeElement(descriptor: SerialDescriptor, index: Int): Boolean {
        encodeInt(index)
        return super.encodeElement(descriptor, index)
    }

    fun encodeSerialized(value: JniSerialized) {
        @Suppress("UNCHECKED_CAST")
        val list = value.list as BasicList<T>
        encodeInt(list.size)
        for (i in 0 until list.size) {
            outputList.add(list[i])
        }
    }
}
