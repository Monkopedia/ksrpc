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

import com.monkopedia.ksrpc.jsonrpc.internal.JsonRpcRequest
import com.monkopedia.ksrpc.jsonrpc.internal.JsonRpcResponse
import com.monkopedia.ksrpc.jsonrpc.internal.JsonRpcTransformer
import com.monkopedia.ksrpc.jsonrpc.internal.JsonRpcWriterBase
import com.monkopedia.ksrpc.ksrpcEnvironment
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.benchmark.TearDown
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

@State(Scope.Benchmark)
open class JsonRpcWriterBenchmark {

    @Param("32", "256", "2048")
    var payloadSize: Int = 0

    private val env = ksrpcEnvironment { }
    private lateinit var scope: CoroutineScope
    private lateinit var writer: JsonRpcWriterBase
    private lateinit var payload: JsonPrimitive

    @Setup
    fun setup() {
        payload = JsonPrimitive("x".repeat(payloadSize))
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        writer = JsonRpcWriterBase(
            scope = scope,
            context = EmptyCoroutineContext,
            env = env,
            comm = LoopbackJsonRpcTransformer()
        )
    }

    @Benchmark
    fun executeLoopbackRoundTrip(): String = runBlocking {
        val response = writer.execute("echo", payload, isNotify = false)
        (response as? JsonPrimitive)?.content ?: ""
    }

    @TearDown
    fun tearDown() {
        runBlocking {
            writer.close()
        }
        scope.cancel()
    }

    private class LoopbackJsonRpcTransformer : JsonRpcTransformer() {
        private val json = Json
        private val responses = Channel<JsonElement>(Channel.UNLIMITED)

        override val isOpen: Boolean
            get() = !responses.isClosedForReceive

        override suspend fun send(message: JsonElement) {
            val request = json.decodeFromJsonElement<JsonRpcRequest>(message)
            val response = JsonRpcResponse(result = request.params, id = request.id)
            responses.send(json.encodeToJsonElement(JsonRpcResponse.serializer(), response))
        }

        override suspend fun receive(): JsonElement? = responses.receiveCatching().getOrNull()

        override fun close(cause: Throwable?) {
            responses.close(cause)
        }
    }
}
