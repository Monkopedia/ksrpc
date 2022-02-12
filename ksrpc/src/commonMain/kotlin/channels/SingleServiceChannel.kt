package com.monkopedia.ksrpc.channels

import com.monkopedia.ksrpc.KsrpcEnvironment.Element
import com.monkopedia.ksrpc.RpcObject
import com.monkopedia.ksrpc.RpcService
import com.monkopedia.ksrpc.channels.SerializedService
import com.monkopedia.ksrpc.rpcObject
import com.monkopedia.ksrpc.serialized

interface SingleServiceChannel : Element {
    suspend fun hostService(service: SerializedService)
}

suspend inline fun <reified T : RpcService> SingleServiceChannel.hostService(service: T) =
    hostService(service, rpcObject())

suspend inline fun <reified T : RpcService> SingleServiceChannel.hostService(
    service: T,
    obj: RpcObject<T>
) = hostService(service.serialized(obj, env))
