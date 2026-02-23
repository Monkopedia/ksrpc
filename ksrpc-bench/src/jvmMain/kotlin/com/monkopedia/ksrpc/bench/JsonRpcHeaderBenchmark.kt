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

import com.monkopedia.ksrpc.jsonrpc.internal.JsonRpcTransformer
import com.monkopedia.ksrpc.jsonrpc.internal.jsonHeader
import com.monkopedia.ksrpc.ksrpcEnvironment
import io.ktor.utils.io.ByteChannel
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.benchmark.TearDown
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

@State(Scope.Benchmark)
open class JsonRpcHeaderBenchmark {

    @Param("32", "256", "2048")
    var payloadSize: Int = 0

    private val env = ksrpcEnvironment { }
    private lateinit var payload: JsonElement
    private lateinit var loopbackChannel: ByteChannel
    private lateinit var transformer: JsonRpcTransformer

    @Setup
    fun setup() {
        payload = JsonPrimitive("x".repeat(payloadSize))
        loopbackChannel = ByteChannel(autoFlush = true)
        transformer = (loopbackChannel to loopbackChannel).jsonHeader(env)
    }

    @Benchmark
    fun sendThenReceive(): String = runBlocking {
        transformer.send(payload)
        (transformer.receive() as? JsonPrimitive)?.content ?: ""
    }

    @TearDown
    fun tearDown() {
        transformer.close(null)
        loopbackChannel.close()
    }
}
