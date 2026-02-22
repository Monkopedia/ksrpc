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

import com.monkopedia.ksrpc.channels.ChannelClient
import com.monkopedia.ksrpc.channels.ChannelId
import com.monkopedia.ksrpc.jni.JniConnection
import com.monkopedia.ksrpc.jni.JniSerialization
import com.monkopedia.ksrpc.jni.JniSerialized
import com.monkopedia.ksrpc.jni.newTypeConverter
import com.monkopedia.ksrpc.jni.withConverter
import com.monkopedia.ksrpc.ksrpcEnvironment
import kotlin.coroutines.suspendCoroutine
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
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.PairSerializer
import kotlinx.serialization.builtins.serializer

@State(Scope.Benchmark)
open class JniCommunicationBenchmark {

    @Param("32", "256", "2048")
    var payloadSize: Int = 0

    private lateinit var scope: CoroutineScope
    private lateinit var payload: Pair<String, String>
    private lateinit var env: com.monkopedia.ksrpc.KsrpcEnvironment<JniSerialized>
    private lateinit var connection: JniConnection
    private lateinit var host: NativeHost
    private val pairSerializer = PairSerializer(String.serializer(), String.serializer())

    @Setup
    fun setup() {
        runBlocking {
            JniLibraryLoader.ensureLoaded()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            payload = "x".repeat(payloadSize) to "tail"
            env = ksrpcEnvironment(JniSerialization()) { }
            host = NativeHost()
            connection = JniConnection(scope, env, host.createEnv())
            suspendCoroutine<Int> {
                host.registerService(connection, it.withConverter(newTypeConverter<Any?>().int))
            }
        }
    }

    @Benchmark
    fun jniRpcRoundTrip(): String = runBlocking {
        val input = env.serialization.createCallData(pairSerializer, payload)
        val output = connection.call(ChannelId(ChannelClient.DEFAULT), "rpc", input)
        env.serialization.decodeCallData(String.serializer(), output)
    }

    @TearDown
    fun tearDown() {
        runBlocking {
            runCatching { connection.close() }
            runCatching { connection.finalize() }
            runCatching { scope.cancel() }
        }
    }
}
