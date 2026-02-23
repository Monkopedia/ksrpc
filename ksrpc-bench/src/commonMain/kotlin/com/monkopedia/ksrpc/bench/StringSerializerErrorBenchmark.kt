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

import com.monkopedia.ksrpc.RpcFailure
import com.monkopedia.ksrpc.channels.CallData
import com.monkopedia.ksrpc.ksrpcEnvironment
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.serialization.builtins.serializer

@State(Scope.Benchmark)
open class StringSerializerErrorBenchmark {

    @Param("32", "256", "2048")
    var payloadSize: Int = 0

    private val env = ksrpcEnvironment { }
    private lateinit var normalCallData: CallData<String>
    private lateinit var errorCallData: CallData<String>
    private lateinit var endpointMissingCallData: CallData<String>

    @Setup
    fun setup() {
        val payload = "x".repeat(payloadSize)
        val failure = RpcFailure(payload)
        normalCallData = env.serialization.createCallData(String.serializer(), payload)
        errorCallData = env.serialization.createErrorCallData(RpcFailure.serializer(), failure)
        endpointMissingCallData =
            env.serialization.createEndpointNotFoundCallData(RpcFailure.serializer(), failure)
    }

    @Benchmark
    fun isErrorNormal(): Boolean = env.serialization.isError(normalCallData)

    @Benchmark
    fun isErrorError(): Boolean = env.serialization.isError(errorCallData)

    @Benchmark
    fun isErrorEndpointMissing(): Boolean = env.serialization.isError(endpointMissingCallData)
}
