package com.monkopedia.jnitest

import com.monkopedia.ksrpc.jni.JniSerialized

class NativeHost {
    external fun serializeDeserialize(x: JniSerialized): JniSerialized
}
