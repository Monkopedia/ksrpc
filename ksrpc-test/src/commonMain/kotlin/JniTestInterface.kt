package com.monkopedia.ksrpc

import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import io.ktor.utils.io.*

val jniTestContent = List(2048) {
    "Test string content"
}.joinToString { "" }

@KsService
interface JniTestInterface : RpcService {
    @KsMethod("/binary_rpc")
    suspend fun binaryRpc(u: Pair<String, String>): ByteReadChannel

    @KsMethod("/input")
    suspend fun inputRpc(u: ByteReadChannel): String

    @KsMethod("/ping")
    suspend fun ping(input: String): String

    @KsMethod("/rpc")
    suspend fun rpc(u: Pair<String, String>): String

    @KsMethod("/service")
    suspend fun subservice(prefix: String): JniTestSubInterface
}

@KsService
interface JniTestSubInterface : RpcService {
    @KsMethod("/rpc")
    suspend fun rpc(u: Pair<String, String>): String
}
