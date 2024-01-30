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
package com.monkopedia.ksrpc

import com.monkopedia.ksrpc.jni.JavaJniContinuation
import com.monkopedia.ksrpc.jni.JniConnection
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

    external fun createEnv(): Long

    external fun registerService(
        connection: JniConnection,
        output: JavaJniContinuation<Int>
    )
}

interface Receiver {
    fun message(str: String)
}
