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
@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.ksrpc.bench

import com.monkopedia.ksrpc.sockets.posixFileReadChannel
import com.monkopedia.ksrpc.sockets.posixFileWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.cancel
import io.ktor.utils.io.close
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import kotlin.system.exitProcess
import kotlin.time.measureTime
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.coroutines.runBlocking
import platform.posix.pipe

fun posixBenchMain(args: Array<String>) {
    val config = parseArgs(args)
    println(
        "POSIX microbench: warmup=${config.warmupIterations}, " +
            "iterations=${config.measureIterations}"
    )
    for (payloadSize in config.payloadSizes) {
        val result = runCase(payloadSize, config.warmupIterations, config.measureIterations)
        println(
            "payloadSize=$payloadSize bytes " +
                "opsPerSec=${result.opsPerSecond} " +
                "nsPerOp=${result.nanosPerOp}"
        )
    }
}

private data class BenchConfig(
    val payloadSizes: List<Int>,
    val warmupIterations: Int,
    val measureIterations: Int
)

private data class BenchResult(val opsPerSecond: Double, val nanosPerOp: Double)

private fun parseArgs(args: Array<String>): BenchConfig {
    var warmup = 2_000
    var iterations = 10_000
    var payloadSizes = listOf(256, 4_096, 16_384)
    for (arg in args) {
        when {
            arg.startsWith("--warmup=") -> warmup = arg.substringAfter('=').toIntOrNull() ?: warmup

            arg.startsWith("--iterations=") ->
                iterations = arg.substringAfter('=').toIntOrNull() ?: iterations

            arg.startsWith("--payloads=") -> {
                payloadSizes = arg.substringAfter('=')
                    .split(',')
                    .mapNotNull { it.trim().toIntOrNull() }
                    .filter { it > 0 }
                if (payloadSizes.isEmpty()) {
                    println("Invalid --payloads argument: $arg")
                    exitProcess(2)
                }
            }
        }
    }
    return BenchConfig(payloadSizes, warmup, iterations)
}

private fun runCase(payloadSize: Int, warmup: Int, iterations: Int): BenchResult {
    val payload = ByteArray(payloadSize) { index -> (index and 0x7F).toByte() }
    val drainBuffer = ByteArray(payloadSize)
    val (readChannel, writeChannel) = createPipeChannels()
    var result: BenchResult? = null

    try {
        runBlocking {
            repeat(warmup) {
                writeThenDrain(writeChannel, readChannel, payload, drainBuffer)
            }
            val elapsedNs = measureTime {
                repeat(iterations) {
                    writeThenDrain(writeChannel, readChannel, payload, drainBuffer)
                }
            }.inWholeNanoseconds
            val opsPerSecond = iterations * 1_000_000_000.0 / elapsedNs
            val nanosPerOp = elapsedNs.toDouble() / iterations
            result = BenchResult(opsPerSecond, nanosPerOp)
        }
    } finally {
        runBlocking {
            writeChannel.close()
            readChannel.cancel()
        }
    }
    return checkNotNull(result)
}

private suspend fun writeThenDrain(
    writeChannel: ByteWriteChannel,
    readChannel: ByteReadChannel,
    payload: ByteArray,
    drainBuffer: ByteArray
) {
    writeChannel.writeFully(payload, 0, payload.size)
    writeChannel.flush()
    var totalRead = 0
    while (totalRead < payload.size) {
        val read = readChannel.readAvailable(drainBuffer, totalRead, payload.size - totalRead)
        check(read > 0) { "Unexpected end of stream while draining benchmark pipe" }
        totalRead += read
    }
}

private fun createPipeChannels(): Pair<ByteReadChannel, ByteWriteChannel> = memScoped {
    val fds = allocArray<IntVar>(2)
    check(pipe(fds) == 0) { "Failed to create POSIX pipe for benchmark" }
    posixFileReadChannel(fds[0]) to posixFileWriteChannel(fds[1])
}
