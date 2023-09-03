package com.monkopedia.ksrpc

import com.monkopedia.ksrpc.jni.JavaJniContinuation
import com.monkopedia.ksrpc.jni.JniSerialized
import com.monkopedia.ksrpc.jni.NativeJniContinuation

class NativeHost {
    external fun serializeDeserialize(x: JniSerialized): JniSerialized
    external fun createContinuations(
        receiver: Receiver,
        list: MutableList<NativeJniContinuation<Int>>
    )

    external fun createContinuationRelay(
        output: JavaJniContinuation<Int>
    ): NativeJniContinuation<Int>
}

interface Receiver {
    fun message(str: String)
}
