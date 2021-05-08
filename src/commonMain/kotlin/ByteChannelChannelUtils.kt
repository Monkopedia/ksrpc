package com.monkopedia.ksrpc

import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.readBytes
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.close
import io.ktor.utils.io.core.readBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

private const val SIZE = 16 * 1024

fun ByteReadChannel.toFrameFlow(maxSize: Int = SIZE): Flow<Frame> = flow {
    while (!isClosedForRead) {
        val packet = readPacket(maxSize, 0)
        emit(Frame.Binary(isClosedForRead, packet.readBytes()))
    }
}

suspend fun ReceiveChannel<Frame>.toReadChannel(onClose: () -> Unit): ByteReadChannel {
    val scope = CoroutineScope(coroutineContext)
    return ByteChannel(autoFlush = true).also { channel ->
        val job = scope.launch {
            while (true) {
                val frame = receive()
                when (frame) {
                    is Frame.Binary -> {
                        val src = frame.readBytes()
                        if (!channel.isClosedForWrite) {
                            channel.writeFully(src, 0, src.size)
                        }
                        if (frame.fin) {
                            channel.close()
                            return@launch
                        }
                    }
                    is Frame.Text -> {
                        throw IllegalStateException("Unexpected response $frame")
                    }
                    is Frame.Close -> {
                        channel.close()
                        return@launch
                    }
                    is Frame.Ping,
                    is Frame.Pong -> {
                    }
                }
            }
        }
        job.invokeOnCompletion {
            onClose()
        }
        channel.attachJob(job)
    }
}