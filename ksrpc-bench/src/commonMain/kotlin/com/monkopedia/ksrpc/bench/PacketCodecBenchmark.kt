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
@file:OptIn(KsrpcInternal::class)

package com.monkopedia.ksrpc.bench

import com.monkopedia.ksrpc.annotation.KsrpcInternal
import com.monkopedia.ksrpc.channels.CallData
import com.monkopedia.ksrpc.ksrpcEnvironment
import com.monkopedia.ksrpc.packets.internal.Packet
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.serialization.builtins.serializer

@State(Scope.Benchmark)
open class PacketCodecBenchmark {

    @Param("32", "256", "2048")
    var payloadSize: Int = 0

    private val env = ksrpcEnvironment { }
    private lateinit var packet: Packet<String>
    private lateinit var encodedPacket: String

    @Setup
    fun setup() {
        val payload = "x".repeat(payloadSize)
        packet = Packet(
            input = true,
            id = "channel-id",
            messageId = "message-id",
            endpoint = "/bench/echo",
            data = payload
        )
        encodedPacket = encodePacket()
    }

    @Benchmark
    fun encodePacket(): String = env.serialization
        .createCallData(Packet.serializer(String.serializer()), packet)
        .readSerialized()

    // Returns `Any` rather than `Packet<String>` so the kotlinx-benchmark generated
    // descriptor classes (in `kotlinx.benchmark.generated.*`) do not need to opt in to
    // `@KsrpcInternal` — they reference the benchmark function's return type.
    @Benchmark
    fun decodePacket(): Any = env.serialization.decodeCallData(
        Packet.serializer(String.serializer()),
        CallData.create(encodedPacket)
    )

    @Benchmark
    fun encodeThenDecodePacket(): Any = env.serialization.decodeCallData(
        Packet.serializer(String.serializer()),
        CallData.create(encodePacket())
    )
}
