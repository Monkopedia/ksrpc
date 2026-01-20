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
@file:Suppress("MemberVisibilityCanBePrivate")

package com.monkopedia.ksrpc.jni

import com.monkopedia.ksrpc.RpcFailure
import com.monkopedia.ksrpc.asString

class NativeJniContinuation<T>(val nativeObject: Long) : JniContinuation<T> {
    var ser: JniSer = JniSer

    protected fun finalize() {
        finalize(nativeObject)
    }

    override fun resumeWith(converter: Converter<*, T>, result: Result<T>) {
        result.onSuccess {
            try {
                resumeSuccess(nativeObject, converter.convertFrom(it))
            } catch (t: Throwable) {
                throw Throwable("resumeWith failed", t)
            }
        }.onFailure {
            val rpcFailure = RpcFailure(it.asString)
            resumeFailure(nativeObject, ser.encodeToJni(RpcFailure.serializer(), rpcFailure))
        }
    }

    external fun finalize(nativeObject: Long)
    external fun resumeSuccess(nativeObject: Long, value: Any?)
    external fun resumeFailure(nativeObject: Long, value: JniSerialized)
}
