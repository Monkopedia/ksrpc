/*
 * Copyright 2021 Jason Monk
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

import io.ktor.util.cio.use
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import java.io.InputStream
import java.io.OutputStream
import kotlin.concurrent.thread
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Job

suspend fun SerializedService.serve(
    input: InputStream,
    output: OutputStream,
    errorListener: ErrorListener = ErrorListener { }
) {
    val channel = ByteChannel(autoFlush = true)
    channel.use {
        val job = coroutineContext[Job]
        thread(start = true) {
            channel.toInputStream(job).copyTo(output)
        }
        defaultHosting(input.toByteReadChannel(), this, errorListener = errorListener)
    }
}

suspend fun Pair<InputStream, OutputStream>.asChannel(): Connection {
    val (input, output) = this
    val channel = ByteChannel(autoFlush = true)
    val job = coroutineContext[Job]
    thread(start = true) {
        channel.toInputStream(job).copyTo(output)
    }
    return (input.toByteReadChannel(coroutineContext) to channel).asChannel()
}
