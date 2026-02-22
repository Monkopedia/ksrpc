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
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.toByteArray
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
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

internal suspend fun callComplexEcho(
    service: SerializedService<String>,
    env: KsrpcEnvironment<String>,
    payload: ComplexEchoPayload
): ComplexEchoPayload {
    val input = env.serialization.createCallData(ComplexEchoPayload.serializer(), payload)
    val output = service.call("complexEcho", input)
    return env.serialization.decodeCallData(ComplexEchoPayload.serializer(), output)
}

internal suspend fun callBinaryEcho(
    service: SerializedService<String>,
    payload: ByteArray
): ByteArray {
    val input = CallData.createBinary<String>(ByteReadChannel(payload))
    val output = service.call("binaryEcho", input)
    return output.readBinary().toByteArray()
}

internal fun createComplexPayload(payloadSize: Int): ComplexEchoPayload {
    val payload = "x".repeat(payloadSize)
    val values = List(8) { payloadSize + it }
    return ComplexEchoPayload(
        title = "payload-$payloadSize",
        text = payload,
        values = values,
        nested = ComplexEchoPayload.NestedPayload(
            enabled = (payloadSize % 2 == 0),
            ratio = payloadSize / 100.0,
            tags = mapOf(
                "size" to payloadSize.toString(),
                "prefix" to payload.take(4)
            )
        ),
        children = List(4) { index ->
            ComplexEchoPayload.ChildPayload(
                name = "child-$index",
                score = index + payloadSize,
                active = (index % 2 == 0)
            )
        }
    )
}

internal fun createBinaryPayload(payloadSize: Int): ByteArray =
    ByteArray(payloadSize) { index -> ((index * 31 + payloadSize) and 0xFF).toByte() }

internal fun binaryDigest(payload: ByteArray): Int {
    var hash = 1
    payload.forEach {
        hash = (hash * 31) xor (it.toInt() and 0xFF)
    }
    return hash
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

@Serializable
internal data class ComplexEchoPayload(
    val title: String,
    val text: String,
    val values: List<Int>,
    val nested: NestedPayload,
    val children: List<ChildPayload>
) {

    @Serializable
    data class NestedPayload(val enabled: Boolean, val ratio: Double, val tags: Map<String, String>)

    @Serializable
    data class ChildPayload(val name: String, val score: Int, val active: Boolean)
}
