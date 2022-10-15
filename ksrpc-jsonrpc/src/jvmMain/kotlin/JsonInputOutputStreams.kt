package com.monkopedia.ksrpc.channels

import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.jsonrpc.asJsonRpcConnection
import com.monkopedia.ksrpc.sockets.internal.swallow
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import io.ktor.utils.io.reader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.nio.channels.Channels
import kotlin.coroutines.coroutineContext

/**
 * Helper that calls into Pair<ByteReadChannel, ByteWriteChannel>.asJsonRpcConnection.
 */
suspend fun Pair<InputStream, OutputStream>.asJsonRpcConnection(
    env: KsrpcEnvironment
): SingleChannelConnection {
    val (input, output) = this
    val channel = GlobalScope.reader(coroutineContext) {
        val outputChannel = Channels.newChannel(output)
        while (!channel.isClosedForRead) {
            channel.read { buffer ->
                outputChannel.write(buffer)
                output.flush()
            }
        }
    }.channel
    return (input.toByteReadChannel(coroutineContext) to channel).asJsonRpcConnection(env)
}

/**
 * Create a [SingleChannelConnection] that communicates over the std in/out streams of this process
 * using jsonrpc.
 */
suspend fun stdInJsonRpcConnection(env: KsrpcEnvironment): SingleChannelConnection {
    val input = System.`in`
    val output = System.out
    return (input to output).asJsonRpcConnection(env)
}

/**
 * Create a [SingleChannelConnection] that starts the process and uses the
 * [Process.getInputStream] and [Process.getOutputStream] as the streams for communication using
 * jsonrpc.
 */
suspend fun ProcessBuilder.asJsonRpcConnection(env: KsrpcEnvironment): SingleChannelConnection {
    val process = redirectInput(ProcessBuilder.Redirect.PIPE)
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .start()
    val input = process.inputStream
    val output = process.outputStream
    return (input to output).asJsonRpcConnection(env).also {
        it.defaultChannel().onClose {
            withContext(Dispatchers.IO) {
                swallow { input.close() }
                swallow { output.close() }
            }
            process.destroyForcibly()
        }
    }
}