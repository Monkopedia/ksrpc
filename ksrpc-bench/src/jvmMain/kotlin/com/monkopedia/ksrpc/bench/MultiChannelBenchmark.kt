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

import com.monkopedia.ksrpc.internal.MultiChannel
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.benchmark.TearDown
import kotlinx.coroutines.runBlocking

@State(Scope.Benchmark)
open class MultiChannelBenchmark {

    private lateinit var intChannel: MultiChannel<Int>
    private lateinit var stringChannel: MultiChannel<String>

    @Setup
    fun setup() {
        intChannel = MultiChannel()
        stringChannel = MultiChannel()
    }

    @Benchmark
    fun allocateSendReceive(): Int = runBlocking {
        val (id, pending) = intChannel.allocateReceive()
        intChannel.send(id.toString(), id)
        pending.await()
    }

    @Benchmark
    fun allocateSendReceiveStringId(): String = runBlocking {
        val (id, pending) = stringChannel.allocateReceiveString()
        stringChannel.send(id, id)
        pending.await()
    }

    @TearDown
    fun tearDown() = runBlocking {
        intChannel.close()
        stringChannel.close()
    }
}
