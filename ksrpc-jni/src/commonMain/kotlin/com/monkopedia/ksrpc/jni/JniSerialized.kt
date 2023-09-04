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
