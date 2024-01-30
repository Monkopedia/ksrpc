@file:UseSerializers(JniSerialized.Companion::class)

package com.monkopedia.ksrpc.jni

import com.monkopedia.ksrpc.CallDataSerializer
import com.monkopedia.ksrpc.RpcFailure
import com.monkopedia.ksrpc.channels.CallData
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

class JniSerialization(private val jniSer: JniSer = JniSer) : CallDataSerializer<JniSerialized> {
    override fun <I> createCallData(serializer: KSerializer<I>, input: I): CallData<JniSerialized> {
        return CallData.create(
            jniSer.encodeToJni(
                Wrapper.serializer(),
                Wrapper(false, jniSer.encodeToJni(serializer, input))
            )
        )
    }

    override fun <I> createErrorCallData(
        serializer: KSerializer<I>,
        input: I
    ): CallData<JniSerialized> {
        return CallData.create(
            jniSer.encodeToJni(
                Wrapper.serializer(),
                Wrapper(true, jniSer.encodeToJni(serializer, input))
            )
        )
    }

    override fun isError(data: CallData<JniSerialized>): Boolean {
        val wrapper = jniSer.decodeFromJni(Wrapper.serializer(), data.readSerialized())
        return wrapper.isError
    }

    override fun decodeErrorCallData(callData: CallData<JniSerialized>): Throwable {
        val wrapper = jniSer.decodeFromJni(Wrapper.serializer(), callData.readSerialized())
        return jniSer.decodeFromJni(RpcFailure.serializer(), wrapper.content).toException()
    }

    override fun <I> decodeCallData(serializer: KSerializer<I>, data: CallData<JniSerialized>): I {
        val wrapper = jniSer.decodeFromJni(Wrapper.serializer(), data.readSerialized())
        return jniSer.decodeFromJni(serializer, wrapper.content)
    }

    @Serializable
    private data class Wrapper(
        val isError: Boolean,
        val content: JniSerialized
    )
}
