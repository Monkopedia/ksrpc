package com.monkopedia.ksrpc.sockets

import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.channels.Connection

/**
 * Create a [Connection] that communicates over the std in/out streams of this process.
 */
actual suspend inline fun withStdInOut(
    ksrpcEnvironment: KsrpcEnvironment,
    withConnection: (Connection) -> Unit
): Unit = error("Unsupported for js targets")