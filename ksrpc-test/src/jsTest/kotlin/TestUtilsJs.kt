/**
 * Copyright (C) 2025 Jason Monk <monkopedia@gmail.com>
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

import com.monkopedia.ksrpc.channels.SerializedService
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import kotlin.js.Promise
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise

actual suspend inline fun httpTest(
    crossinline serve: suspend Routing.() -> Unit,
    test: suspend (Int) -> Unit,
    isWebsocket: Boolean
) {
    // Do nothing, disable HTTP hosting in JS tests.
}

actual suspend fun Routing.testServe(
    basePath: String,
    channel: SerializedService<String>,
    env: KsrpcEnvironment<String>
) {
    // Do nothing, disable HTTP hosting in JS tests.
}

actual fun Routing.testServeWebsocket(
    basePath: String,
    channel: SerializedService<String>,
    env: KsrpcEnvironment<String>
) {
    // Do nothing, disable HTTP hosting in JS tests.
}

actual fun createPipe(): Pair<ByteWriteChannel, ByteReadChannel> {
    val channel = ByteChannel(autoFlush = true)
    return channel to channel
}

actual interface Routing

external class RetPromise : Promise<Any?>
actual typealias RunBlockingReturn = RetPromise

@OptIn(DelicateCoroutinesApi::class)
internal actual fun runBlockingUnit(
    function: suspend CoroutineScope.() -> Unit
): RunBlockingReturn {
    @Suppress("UnsafeCastFromDynamic")
    return GlobalScope.promise {
        function()
    }.asDynamic()
}
