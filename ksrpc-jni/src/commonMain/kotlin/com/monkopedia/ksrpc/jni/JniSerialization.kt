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
    override fun <I> createCallData(serializer: KSerializer<I>, input: I): CallData<JniSerialized> =
        CallData.create(
            jniSer.encodeToJni(
                Wrapper.serializer(),
                Wrapper(false, jniSer.encodeToJni(serializer, input))
            )
        )

    override fun <I> createErrorCallData(
        serializer: KSerializer<I>,
        input: I
    ): CallData<JniSerialized> = CallData.create(
        jniSer.encodeToJni(
            Wrapper.serializer(),
            Wrapper(true, jniSer.encodeToJni(serializer, input))
        )
    )

    override fun <I> createEndpointNotFoundCallData(
        serializer: KSerializer<I>,
        input: I
    ): CallData<JniSerialized> = CallData.create(
        jniSer.encodeToJni(
            Wrapper.serializer(),
            Wrapper(true, jniSer.encodeToJni(serializer, input), isEndpointMissing = true)
        )
    )

    override fun isError(data: CallData<JniSerialized>): Boolean {
        val wrapper = jniSer.decodeFromJni(Wrapper.serializer(), data.readSerialized())
        return wrapper.isError
    }

    override fun decodeErrorCallData(callData: CallData<JniSerialized>): Throwable {
        val wrapper = jniSer.decodeFromJni(Wrapper.serializer(), callData.readSerialized())
        return if (wrapper.isEndpointMissing) {
            val failure = jniSer.decodeFromJni(RpcFailure.serializer(), wrapper.content)
            RpcEndpointException(failure.stack)
        } else {
            jniSer.decodeFromJni(RpcFailure.serializer(), wrapper.content).toException()
        }
    }

    override fun <I> decodeCallData(serializer: KSerializer<I>, data: CallData<JniSerialized>): I {
        val wrapper = jniSer.decodeFromJni(Wrapper.serializer(), data.readSerialized())
        return jniSer.decodeFromJni(serializer, wrapper.content)
    }

    @Serializable
    private data class Wrapper(
        val isError: Boolean,
        val content: JniSerialized,
        val isEndpointMissing: Boolean = false
    )
}
