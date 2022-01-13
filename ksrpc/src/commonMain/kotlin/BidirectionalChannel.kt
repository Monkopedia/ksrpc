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

import kotlin.coroutines.coroutineContext
import kotlin.jvm.JvmName
import kotlinx.coroutines.CoroutineScope

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
