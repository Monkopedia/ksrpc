package com.monkopedia.ksrpc

import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.coroutineContext
import kotlin.jvm.JvmName

interface BidirectionalChannel {
    suspend fun CoroutineScope.receivingChannel(): SerializedChannel
    suspend fun CoroutineScope.serve(sendingChannel: SerializedChannel)
}

internal expect interface VoidService : RpcService

suspend inline fun <reified T : RpcService, reified R : RpcService> BidirectionalChannel.connect(
    scope: CoroutineScope? = null,
    host: (R) -> T
) = connect(scope) { channel ->
    host(channel.toStub()).serialized()
}

@JvmName("connectSerialized")
suspend inline fun BidirectionalChannel.connect(
    scope: CoroutineScope? = null,
    host: (SerializedChannel) -> SerializedChannel
) {
    val scope = scope ?: CoroutineScope(coroutineContext)
    val recv = scope.receivingChannel()
    val serializedHost = host(recv)
    scope.serve(serializedHost)
}