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
import com.monkopedia.ksrpc.channels.CallData
import kotlinx.serialization.KSerializer
import kotlinx.serialization.UseSerializers

/**
 * Wire-format adapter for the JNI transport. Round-trips successful payloads through
 * [JniSer]; error frames are carried as [CallData.Error] by the routing layer (RpcMethod)
 * and encoded natively by [JniConnection], so this serializer only handles the success
 * path.
 */
class JniSerialization(private val jniSer: JniSer = JniSer) : CallDataSerializer<JniSerialized> {
    override fun <I> createCallData(serializer: KSerializer<I>, input: I): CallData<JniSerialized> =
        CallData.create(jniSer.encodeToJni(serializer, input))

    override fun <I> decodeCallData(serializer: KSerializer<I>, data: CallData<JniSerialized>): I =
        jniSer.decodeFromJni(serializer, data.readSerialized())
}
