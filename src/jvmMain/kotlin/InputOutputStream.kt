/*
 * Copyright 2020 Jason Monk
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
package com.monkopedia.ksrpc

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.completeWith
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal const val CONTENT_LENGTH = "Content-Length"
internal const val METHOD = "Method"

suspend fun SerializedChannel.serve(
    input: InputStream,
    output: OutputStream,
    errorListener: ErrorListener = ErrorListener { }
) {
    val channel = this
    val lock = Mutex()
    val reader = input.bufferedReader()
    val writer = output.bufferedWriter()

    while (true) {
        try {
            coroutineScope {
                flow {
                    while (true) {
                        lock.lock()
                        try {
                            val params = reader.readFields()
                            val method = params[METHOD] ?: error("Missing method")
                            val str = reader.readContent(params)
                            lock.unlock()
                            emit(
                                async(Dispatchers.IO) {
                                    channel.call(method, str)
                                }
                            )
                        } catch (t: Throwable) {
                            lock.unlock()
                            throw t
                        }
                    }
                }.buffer(10).flowOn(Dispatchers.IO).map {
                    it.await()
                }.buffer().onEach { output ->
                    writer.writeContent(output)
                }.collect()
            }
        } catch (t: Throwable) {
            errorListener.onError(t)
            input.close()
            output.close()
            return
        }
    }
}

fun Pair<InputStream, OutputStream>.asChannel(): SerializedChannel {
    val reader = first.bufferedReader()
    val writer = second.bufferedWriter()
    val lock = Mutex()
    val calls = Channel<Pair<String, String>>(5)
    val responses = Channel<CompletableDeferred<String>>(5)
    GlobalScope.launch {
        try {
            while (true) {
                val callback = responses.receive()
                callback.completeWith(
                    Result.runCatching {
                        var params = reader.readFields()
                        if (params.isEmpty()) {
                            params = reader.readFields()
                        }
                        reader.readContent(params)
                    }
                )
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            responses.close(t)
        }
    }
    GlobalScope.launch {
        try {
            while (true) {
                val (str, input) = calls.receive()
                writer.appendLine("$METHOD: $str")
                writer.writeContent(input)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            calls.close(t)
        }
    }

    return object : SerializedChannel {
        override suspend fun call(str: String, input: String): String {
            val response = CompletableDeferred<String>()
            lock.withLock {
                calls.send(str to input)
                responses.send(response)
            }
            return response.await()
        }

        override suspend fun close() {
            reader.close()
            writer.close()
        }
    }
}

private fun BufferedWriter.writeContent(
    content: String
) {
    appendLine("$CONTENT_LENGTH: ${content.length}")
    appendLine()
    append(content)
    flush()
}

private fun BufferedReader.readContent(
    params: Map<String, String>
): String {
    var length = params[CONTENT_LENGTH]?.toIntOrNull() ?: error("Missing content length in $params")
    val buffer = CharArray(length)
    var byteArray = ByteArrayOutputStream()
    var readLength = read(buffer)
    while (length > 0 && readLength >= 0) {
        byteArray.write(String(buffer, 0, readLength).toByteArray())
        length -= readLength
        readLength = read(buffer, 0, length)
    }
    return String(byteArray.toByteArray())
}

private fun BufferedReader.readFields(): Map<String, String> {
    val fields = mutableListOf<String>()
    var line = readLine()
    while (line == null || line.isNotEmpty()) {
        if (line != null) {
            fields.add(line)
        }
        line = readLine()
    }
    return parseParams(fields)
}

fun parseParams(fields: List<String>): Map<String, String> {
    return fields.filter { it.contains(":") }.map {
        val (first, second) = it.split(":")
        return@map first.trim() to second.trim()
    }.toMap()
}
