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

import java.io.PrintWriter
import java.io.StringWriter

suspend fun SerializedChannel.serveOnStd() {
    val input = System.`in`
    val output = System.out
    serve(input, output)
}

suspend fun System.rpcChannel(): SerializedChannel {
    val input = System.`in`
    val output = System.out
    return (input to output).asChannel()
}

suspend fun ProcessBuilder.asChannel(): SerializedChannel {
    val process = redirectInput(ProcessBuilder.Redirect.PIPE)
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .start()
    val input = process.inputStream
    val output = process.outputStream
    return (input to output).asChannel()
}

suspend fun SerializedChannel.serveTo(process: ProcessBuilder) {
    val process = process.redirectInput(ProcessBuilder.Redirect.PIPE)
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .start()
    val input = process.inputStream
    val output = process.outputStream
    serve(input, output)
}

actual val Throwable.asString: String
    get() = StringWriter().also {
        printStackTrace(PrintWriter(it))
    }.toString()
