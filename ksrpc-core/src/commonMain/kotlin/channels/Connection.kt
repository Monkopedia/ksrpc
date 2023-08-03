/**
 * Copyright (C) 2022 Jason Monk <monkopedia@gmail.com>
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
package com.monkopedia.ksrpc.channels

import com.monkopedia.ksrpc.RpcService
import com.monkopedia.ksrpc.serialized
import com.monkopedia.ksrpc.toStub
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.jvm.JvmName

/**
 * A bidirectional channel that can both host and call services/sub-services.
 *
 * (Meaning @KsServices can be used for both input and output of any @KsMethod)
 */
interface Connection<T> : ChannelHost<T>, ChannelClient<T>, SingleChannelConnection<T>

/**
 * A bidirectional channel that can host one service in each direction (1 host and 1 client).
 */
interface SingleChannelConnection<T> : SingleChannelHost<T>, SingleChannelClient<T>

// Problems with JS compiler and serialization
data class ChannelId(val id: String)

internal expect interface VoidService : RpcService

/**
 * Connects both default channels for a connection (incoming and outgoing).
 *
 * Provides the [host] lambda with a stub connected to the default outgoing channel
 * for the connection, and then connects the returned service as the hosted channel for
 * the connection.
 *
 * This is equivalent to calling [registerDefault] for [T] instance and using
 * [defaultChannel] and [toStub] to create [R].
 */
@OptIn(ExperimentalContracts::class)
suspend inline fun <reified T : RpcService, reified R : RpcService, S> SingleChannelConnection<S>.connect(
    crossinline host: suspend (R) -> T
) {
    contract {
        callsInPlace(host, InvocationKind.EXACTLY_ONCE)
    }
    connect<S> { channel ->
        host(channel.toStub()).serialized(env)
    }
}

/**
 * Raw version of [connect], performing the same functionality with [SerializedService] directly.
 */
@JvmName("connectSerialized")
@OptIn(ExperimentalContracts::class)
suspend fun <T> SingleChannelConnection<T>.connect(
    host: suspend (SerializedService<T>) -> SerializedService<T>
) {
    contract {
        callsInPlace(host, InvocationKind.EXACTLY_ONCE)
    }
    val recv = defaultChannel()
    val serializedHost = host(recv)
    registerDefault(serializedHost)
}
