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
package com.monkopedia.ksrpc.bench

import com.monkopedia.ksrpc.jni.JniSer
import com.monkopedia.ksrpc.jni.JniSerialized
import com.monkopedia.ksrpc.jni.decodeFromJni
import com.monkopedia.ksrpc.jni.encodeToJni
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.serialization.Serializable

@State(Scope.Benchmark)
open class JniSerializationBenchmark {

    @Param("32", "256", "2048")
    var payloadSize: Int = 0

    private lateinit var payload: JniPayload
    private lateinit var encodedPayload: JniSerialized

    @Setup
    fun setup() {
        JniLibraryLoader.ensureLoaded()
        payload = JniPayload(
            text = "x".repeat(payloadSize),
            numbers = List(8) { payloadSize + it },
            nested = JniNestedPayload(
                enabled = true,
                ratio = payloadSize / 10.0
            )
        )
        encodedPayload = JniSer.encodeToJni(payload)
    }

    @Benchmark
    fun encode(): JniSerialized = JniSer.encodeToJni(payload)

    @Benchmark
    fun decode(): JniPayload = JniSer.decodeFromJni(encodedPayload)

    @Benchmark
    fun encodeThenDecode(): JniPayload = JniSer.decodeFromJni(JniSer.encodeToJni(payload))

    @Serializable
    data class JniPayload(val text: String, val numbers: List<Int>, val nested: JniNestedPayload)

    @Serializable
    data class JniNestedPayload(val enabled: Boolean, val ratio: Double)
}
