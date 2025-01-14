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

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

open class JniSer(
    val encoder: JniEncoder<*>,
    val decoder: JniDecoder<*>
) {

    private constructor(jniBuilder: JniBuilder<Any>) : this(jniBuilder.encoder, jniBuilder.decoder)
    constructor(builder: JniBuilder<*>.() -> Unit = {}) : this(JniBuilder<Any>().also(builder))

    fun <T> decodeFromJni(
        serializationStrategy: DeserializationStrategy<T>,
        jniSerialized: JniSerialized
    ): T {
        return serializationStrategy.deserialize(decoder.decoderFor(jniSerialized))
    }

    fun <T> encodeToJni(serializationStrategy: SerializationStrategy<T>, input: T): JniSerialized {
        return encoder.copy().also {
            serializationStrategy.serialize(it, input)
        }.serialized
    }

    companion object : JniSer()
}

inline fun <reified T> JniSer.decodeFromJni(jniSerialized: JniSerialized): T {
    return decodeFromJni(serializer(), jniSerialized)
}

inline fun <reified T> JniSer.encodeToJni(value: T): JniSerialized {
    return encodeToJni(serializer(), value)
}

class JniBuilder<T> internal constructor() {

    var serializersModule: SerializersModule = EmptySerializersModule()

    var typeConverter: JniTypeConverter<T> = newTypeConverter()
    val encoder: JniEncoder<T>
        get() = JniEncoder(serializersModule, typeConverter)

    val decoder: JniDecoder<T>
        get() = JniDecoder(serializersModule, typeConverter, newList())
}
