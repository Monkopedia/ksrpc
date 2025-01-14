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
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

data class JniSerialized(val list: BasicList<*>) {
    companion object : KSerializer<JniSerialized> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor(
                "com.monkopedia.ksrpc.jni.JniSerialized",
                PrimitiveKind.STRING
            )

        override fun deserialize(decoder: Decoder): JniSerialized {
            return (decoder as JniDecoder<*>).decodeSerialized()
        }

        override fun serialize(encoder: Encoder, value: JniSerialized) {
            (encoder as JniEncoder<*>).encodeSerialized(value)
        }
    }
}
