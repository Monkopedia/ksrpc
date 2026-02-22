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

import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.channels.CallData
import com.monkopedia.ksrpc.channels.SerializedService
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.serializer

internal class EchoSerializedService(override val env: KsrpcEnvironment<String>) :
    SerializedService<String> {
    private val onCloseHandlers = mutableSetOf<suspend () -> Unit>()

    override suspend fun call(endpoint: String, input: CallData<String>): CallData<String> = input

    override suspend fun close() {
        onCloseHandlers.forEach { it.invoke() }
    }

    override suspend fun onClose(onClose: suspend () -> Unit) {
        onCloseHandlers.add(onClose)
    }
}

internal suspend fun callEcho(
    service: SerializedService<String>,
    env: KsrpcEnvironment<String>,
    payload: String
): String {
    val input = env.serialization.createCallData(String.serializer(), payload)
    val output = service.call("echo", input)
    return env.serialization.decodeCallData(String.serializer(), output)
}

internal fun waitForPortOpen(
    host: String,
    port: Int,
    totalTimeoutMillis: Long = 5_000,
    connectTimeoutMillis: Int = 200,
    retryDelayMillis: Long = 25
) {
    val deadline = System.nanoTime() + (totalTimeoutMillis * 1_000_000L)
    var lastError: Throwable? = null
    while (System.nanoTime() < deadline) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), connectTimeoutMillis)
            }
            return
        } catch (t: Throwable) {
            lastError = t
            Thread.sleep(retryDelayMillis)
        }
    }
    throw IllegalStateException(
        "Port $host:$port did not become reachable within ${totalTimeoutMillis}ms",
        lastError
    )
}

internal class TimedRunner(name: String) : AutoCloseable {
    private val runnerName = name
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "$runnerName-runner").apply { isDaemon = true }
    }

    fun <T> run(timeoutMillis: Long = 5_000, block: suspend () -> T): T {
        val task = executor.submit<T> { runBlocking(Dispatchers.Default) { block() } }
        return try {
            task.get(timeoutMillis, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            task.cancel(true)
            throw IllegalStateException("$runnerName timed out after ${timeoutMillis}ms", e)
        } catch (e: ExecutionException) {
            val cause = e.cause ?: e
            when (cause) {
                is RuntimeException -> throw cause
                is Error -> throw cause
                else -> throw IllegalStateException("$runnerName failed", cause)
            }
        }
    }

    override fun close() {
        executor.shutdownNow()
    }
}
