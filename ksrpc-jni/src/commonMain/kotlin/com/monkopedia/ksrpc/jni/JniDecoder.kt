/*
 * Copyright (C) 2026 Jason Monk <monkopedia@gmail.com>
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
        structEnds.add(typeConverter.int.convertTo(next()))
        return super.beginStructure(descriptor)
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        structEnds.removeLast()
    }

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int =
        if (index < structEnds.last()) {
            typeConverter.int.convertTo(next())
        } else {
            CompositeDecoder.DECODE_DONE
        }

    override fun decodeNotNullMark(): Boolean = next()?.let {
        typeConverter.boolean.convertTo(it)
    } ?: false

    override fun decodeNull(): Nothing? = null

    override fun decodeBoolean(): Boolean = typeConverter.boolean.convertTo(next())

    override fun decodeByte(): Byte = typeConverter.byte.convertTo(next())

    override fun decodeShort(): Short = typeConverter.short.convertTo(next())

    override fun decodeInt(): Int = typeConverter.int.convertTo(next())

    override fun decodeLong(): Long = typeConverter.long.convertTo(next())

    override fun decodeFloat(): Float = typeConverter.float.convertTo(next())

    override fun decodeDouble(): Double = typeConverter.double.convertTo(next())

    override fun decodeChar(): Char = typeConverter.char.convertTo(next())

    override fun decodeString(): String = typeConverter.string.convertTo(next())

    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int =
        typeConverter.int.convertTo(next())

    fun decodeSerialized(): JniSerialized {
        val list = newList<T>()
        for (i in 0 until decodeInt()) {
            list.add(next())
        }
        return JniSerialized(list)
    }
}
