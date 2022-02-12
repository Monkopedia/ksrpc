package com.monkopedia.ksrpc.internal.jsonrpc

import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.channels.CONTENT_LENGTH
import com.monkopedia.ksrpc.channels.CONTENT_TYPE
import com.monkopedia.ksrpc.channels.DEFAULT_CONTENT_TYPE
import com.monkopedia.ksrpc.internal.appendLine
import com.monkopedia.ksrpc.internal.readFields
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readFully
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

internal abstract class JsonRpcTransformer<I, O> {
    abstract val isOpen: Boolean

    abstract suspend fun send(message: I)
    abstract suspend fun receive(): O?
    abstract fun close(cause: Throwable?)
}

internal fun Pair<ByteReadChannel, ByteWriteChannel>.jsonHeaderSender(
    env: KsrpcEnvironment
): JsonRpcTransformer<JsonRpcRequest, JsonRpcResponse> =
    JsonRpcHeader(env, first, second, serializer(), serializer())

internal fun Pair<ByteReadChannel, ByteWriteChannel>.jsonHeaderReceiver(
    env: KsrpcEnvironment
): JsonRpcTransformer<JsonRpcResponse, JsonRpcRequest> =
    JsonRpcHeader(env, first, second, serializer(), serializer())

internal class JsonRpcHeader<I, O>(
    env: KsrpcEnvironment,
    private val input: ByteReadChannel,
    private val output: ByteWriteChannel,
    private val inputSerializer: KSerializer<I>,
    private val outputSerializer: KSerializer<O>,
) : JsonRpcTransformer<I, O>() {
    private val json = (env.serialization as? Json) ?: Json
    private val sendLock = Mutex()
    private val receiveLock = Mutex()

    override val isOpen: Boolean
        get() = !input.isClosedForRead

    override suspend fun send(message: I) {
        sendLock.withLock {
            val content = json.encodeToString(inputSerializer, message)
            val contentBytes = content.encodeToByteArray()
            output.appendLine("$CONTENT_LENGTH: ${contentBytes.size}")
            output.appendLine("$CONTENT_TYPE: $DEFAULT_CONTENT_TYPE")
            output.appendLine()
            output.writeFully(contentBytes, 0, contentBytes.size)
            output.flush()
        }
    }

    override suspend fun receive(): O? {
        receiveLock.withLock {
            val params = input.readFields()
            val length = params[CONTENT_LENGTH]?.toIntOrNull() ?: return null
            var byteArray = ByteArray(length)
            input.readFully(byteArray)
            return json.decodeFromString(outputSerializer, byteArray.decodeToString())
        }
    }

    override fun close(cause: Throwable?) {
        output.close(cause)
    }
}

internal fun Pair<ByteReadChannel, ByteWriteChannel>.jsonLineSender(
    env: KsrpcEnvironment
): JsonRpcTransformer<JsonRpcRequest, JsonRpcResponse> =
    JsonRpcLine(env, first, second, serializer(), serializer())

internal fun Pair<ByteReadChannel, ByteWriteChannel>.jsonLineReceiver(
    env: KsrpcEnvironment
): JsonRpcTransformer<JsonRpcResponse, JsonRpcRequest> =
    JsonRpcLine(env, first, second, serializer(), serializer())

internal class JsonRpcLine<I, O>(
    env: KsrpcEnvironment,
    private val input: ByteReadChannel,
    private val output: ByteWriteChannel,
    private val inputSerializer: KSerializer<I>,
    private val outputSerializer: KSerializer<O>,
) : JsonRpcTransformer<I, O>() {
    private val json = (env.serialization as? Json) ?: Json
    private val sendLock = Mutex()
    private val receiveLock = Mutex()

    override val isOpen: Boolean
        get() = !input.isClosedForRead

    override suspend fun send(message: I) {
        sendLock.withLock {
            val content = json.encodeToString(inputSerializer, message)
            require('\n' !in content) {
                "Cannot have new-lines in encoding check environment json config"
            }
            output.appendLine(content)
            output.flush()
        }
    }

    override suspend fun receive(): O? {
        receiveLock.withLock {
            val line = input.readUTF8Line() ?: return null
            return json.decodeFromString(outputSerializer, line)
        }
    }

    override fun close(cause: Throwable?) {
        output.close(cause)
    }
}
