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
@file:UseSerializers(JniSerialized.Companion::class)

package com.monkopedia.ksrpc.jni

import com.monkopedia.ksrpc.CallDataSerializer
import com.monkopedia.ksrpc.RpcEndpointException
import com.monkopedia.ksrpc.RpcFailure
import com.monkopedia.ksrpc.channels.CallData
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

class JniSerialization(private val jniSer: JniSer = JniSer) : CallDataSerializer<JniSerialized> {
    private val typeConverter = newTypeConverter<Any?>()
    private val booleanConverter = typeConverter.boolean
    private val intConverter = typeConverter.int

    override fun <I> createCallData(serializer: KSerializer<I>, input: I): CallData<JniSerialized> =
        CallData.create(
            encodeEnvelope(
                isError = false,
                isEndpointMissing = false,
                content = jniSer.encodeToJni(serializer, input)
            )
        )

    override fun <I> createErrorCallData(
        serializer: KSerializer<I>,
        input: I
    ): CallData<JniSerialized> = CallData.create(
        encodeEnvelope(
            isError = true,
            isEndpointMissing = false,
            content = jniSer.encodeToJni(serializer, input)
        )
    )

    override fun <I> createEndpointNotFoundCallData(
        serializer: KSerializer<I>,
        input: I
    ): CallData<JniSerialized> = CallData.create(
        encodeEnvelope(
            isError = true,
            isEndpointMissing = true,
            content = jniSer.encodeToJni(serializer, input)
        )
    )

    override fun isError(data: CallData<JniSerialized>): Boolean =
        decodeEnvelope(data.readSerialized()).isError

    override fun decodeErrorCallData(callData: CallData<JniSerialized>): Throwable {
        val envelope = decodeEnvelope(callData.readSerialized())
        return if (envelope.isEndpointMissing) {
            val failure = jniSer.decodeFromJni(RpcFailure.serializer(), envelope.content)
            RpcEndpointException(failure.stack)
        } else {
            jniSer.decodeFromJni(RpcFailure.serializer(), envelope.content).toException()
        }
    }

    override fun <I> decodeCallData(serializer: KSerializer<I>, data: CallData<JniSerialized>): I {
        val envelope = decodeEnvelope(data.readSerialized())
        return jniSer.decodeFromJni(serializer, envelope.content)
    }

    private fun encodeEnvelope(
        isError: Boolean,
        isEndpointMissing: Boolean,
        content: JniSerialized
    ): JniSerialized {
        val out = newList<Any?>()
        out.add(booleanConverter.convertFrom(isError))
        out.add(booleanConverter.convertFrom(isEndpointMissing))
        out.add(intConverter.convertFrom(content.list.size))
        for (index in 0 until content.list.size) {
            out.add(content.list[index])
        }
        return out.asSerialized
    }

    private fun decodeEnvelope(serialized: JniSerialized): Envelope = runCatching {
        decodeFlatEnvelope(serialized)
    }.getOrElse {
        val wrapper = jniSer.decodeFromJni(Wrapper.serializer(), serialized)
        Envelope(
            isError = wrapper.isError,
            isEndpointMissing = wrapper.isEndpointMissing,
            content = wrapper.content
        )
    }

    private fun decodeFlatEnvelope(serialized: JniSerialized): Envelope {
        @Suppress("UNCHECKED_CAST")
        val list = serialized.list as BasicList<Any?>
        if (list.size < ENVELOPE_HEADER_SIZE) {
            error("Invalid Jni envelope size: ${list.size}")
        }

        val isError = booleanConverter.convertTo(list[0])
        val isEndpointMissing = booleanConverter.convertTo(list[1])
        val payloadSize = intConverter.convertTo(list[2])
        if (payloadSize < 0) {
            error("Invalid Jni envelope payload size: $payloadSize")
        }
        val payloadOffset = ENVELOPE_HEADER_SIZE
        val payloadEndExclusive = payloadOffset + payloadSize
        if (payloadEndExclusive < payloadOffset || payloadEndExclusive > list.size) {
            error(
                "Invalid Jni envelope payload bounds: " +
                    "offset=$payloadOffset size=$payloadSize listSize=${list.size}"
            )
        }
        return Envelope(
            isError = isError,
            isEndpointMissing = isEndpointMissing,
            content = JniSerialized(SlicedBasicList(list, payloadOffset, payloadSize))
        )
    }

    @Serializable
    private data class Wrapper(
        val isError: Boolean,
        val content: JniSerialized,
        val isEndpointMissing: Boolean = false
    )

    private data class Envelope(
        val isError: Boolean,
        val isEndpointMissing: Boolean,
        val content: JniSerialized
    )

    private companion object {
        const val ENVELOPE_HEADER_SIZE = 3
    }
}
