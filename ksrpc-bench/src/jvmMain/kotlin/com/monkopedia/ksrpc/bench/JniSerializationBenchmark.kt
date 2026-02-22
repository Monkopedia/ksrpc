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

package com.monkopedia.ksrpc.bench

import com.monkopedia.ksrpc.jni.JniSer
import com.monkopedia.ksrpc.jni.JniSerialized
import com.monkopedia.ksrpc.jni.decodeFromJni
import com.monkopedia.ksrpc.jni.encodeToJni
import com.monkopedia.ksrpc.jni.toList
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

@State(Scope.Benchmark)
open class JniSerializationBenchmark {

    @Param("32", "256", "2048")
    var payloadSize: Int = 0

    private lateinit var payload: JniPayload
    private lateinit var encodedPayload: JniSerialized
    private lateinit var complexPayload: JniComplexPayload
    private lateinit var encodedComplexPayload: JniSerialized
    private lateinit var binaryPayload: JniBinaryPayload
    private lateinit var encodedBinaryPayload: JniSerialized
    private lateinit var nestedSerializedPayload: JniNestedSerializedPayload
    private lateinit var encodedNestedSerializedPayload: JniSerialized

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
        complexPayload = JniComplexPayload(
            id = payloadSize.toLong(),
            name = "payload-$payloadSize",
            flags = List(8) { (it + payloadSize) % 2 == 0 },
            tags = List(6) { index -> "tag-${payloadSize + index}" }.toSet(),
            metrics = List(6) { index -> "m$index" to ((payloadSize + index) / 7.0) }.toMap(),
            nodes =
                List(6) { nodeIndex ->
                    JniComplexNode(
                        key = "node-$nodeIndex",
                        count = payloadSize + nodeIndex,
                        values = List(12) { valueIndex -> payloadSize + nodeIndex + valueIndex },
                        metadata =
                            mapOf(
                                "index" to nodeIndex.toString(),
                                "payload" to payloadSize.toString(),
                                "note" to if (nodeIndex % 2 == 0) "even" else null
                            )
                    )
                },
            variant =
                if (payloadSize % 2 == 0) {
                    JniComplexVariant.Message("variant-$payloadSize", payloadSize / 2)
                } else {
                    JniComplexVariant.Error(payloadSize, "odd-size")
                },
            optional = if (payloadSize % 3 == 0) null else "optional-$payloadSize"
        )
        encodedComplexPayload = JniSer.encodeToJni(complexPayload)
        binaryPayload = JniBinaryPayload(
            raw = ByteArray(payloadSize) { index -> ((index * 31) xor payloadSize).toByte() },
            chunks =
                List(4) { chunkIndex ->
                    ByteArray((payloadSize / 4).coerceAtLeast(8)) { index ->
                        (index + chunkIndex * 17 + payloadSize).toByte()
                    }
                },
            footer = payloadSize
        )
        encodedBinaryPayload = JniSer.encodeToJni(binaryPayload)
        nestedSerializedPayload = JniNestedSerializedPayload(
            header = "nested-$payloadSize",
            content = encodedComplexPayload,
            trailer = payloadSize * 10L
        )
        encodedNestedSerializedPayload = JniSer.encodeToJni(nestedSerializedPayload)
    }

    @Benchmark
    fun encode(): JniSerialized = JniSer.encodeToJni(payload)

    @Benchmark
    fun decode(): JniPayload = JniSer.decodeFromJni(encodedPayload)

    @Benchmark
    fun encodeThenDecode(): JniPayload = JniSer.decodeFromJni(JniSer.encodeToJni(payload))

    @Benchmark
    fun encodeComplex(): JniSerialized = JniSer.encodeToJni(complexPayload)

    @Benchmark
    fun decodeComplex(): JniComplexPayload = JniSer.decodeFromJni(encodedComplexPayload)

    @Benchmark
    fun encodeThenDecodeComplex(): JniComplexPayload =
        JniSer.decodeFromJni(JniSer.encodeToJni(complexPayload))

    @Benchmark
    fun encodeBinary(): JniSerialized = JniSer.encodeToJni(binaryPayload)

    @Benchmark
    fun decodeBinary(): JniBinaryPayload = JniSer.decodeFromJni(encodedBinaryPayload)

    @Benchmark
    fun encodeThenDecodeBinary(): JniBinaryPayload =
        JniSer.decodeFromJni(JniSer.encodeToJni(binaryPayload))

    @Benchmark
    fun encodeNestedSerialized(): JniSerialized = JniSer.encodeToJni(nestedSerializedPayload)

    @Benchmark
    fun decodeNestedSerialized(): JniNestedSerializedPayload =
        JniSer.decodeFromJni(encodedNestedSerializedPayload)

    @Benchmark
    fun decodeNestedSerializedContent(): JniComplexPayload {
        val decoded =
            JniSer.decodeFromJni<JniNestedSerializedPayload>(
                encodedNestedSerializedPayload
            )
        return JniSer.decodeFromJni(decoded.content)
    }

    @Benchmark
    fun decodeNestedSerializedToJvmListSize(): Int {
        val decoded =
            JniSer.decodeFromJni<JniNestedSerializedPayload>(
                encodedNestedSerializedPayload
            )
        return toList(decoded.content).size
    }

    @Serializable
    data class JniPayload(val text: String, val numbers: List<Int>, val nested: JniNestedPayload)

    @Serializable
    data class JniNestedPayload(val enabled: Boolean, val ratio: Double)

    @Serializable
    data class JniComplexPayload(
        val id: Long,
        val name: String,
        val flags: List<Boolean>,
        val tags: Set<String>,
        val metrics: Map<String, Double>,
        val nodes: List<JniComplexNode>,
        val variant: JniComplexVariant,
        val optional: String?
    )

    @Serializable
    data class JniComplexNode(
        val key: String,
        val count: Int,
        val values: List<Int>,
        val metadata: Map<String, String?>
    )

    @Serializable
    sealed interface JniComplexVariant {
        @Serializable
        data class Message(val text: String, val level: Int) : JniComplexVariant

        @Serializable
        data class Error(val code: Int, val reason: String) : JniComplexVariant
    }

    @Serializable
    data class JniBinaryPayload(val raw: ByteArray, val chunks: List<ByteArray>, val footer: Int)

    @Serializable
    data class JniNestedSerializedPayload(
        val header: String,
        val content: JniSerialized,
        val trailer: Long
    )
}
