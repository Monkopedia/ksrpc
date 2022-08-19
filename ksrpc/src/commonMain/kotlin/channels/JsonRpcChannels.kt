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
package com.monkopedia.ksrpc.channels

import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.internal.jsonrpc.JsonRpcWriterBase
import com.monkopedia.ksrpc.internal.jsonrpc.jsonHeader
import com.monkopedia.ksrpc.internal.jsonrpc.jsonLine
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.coroutineContext

suspend fun Pair<ByteReadChannel, ByteWriteChannel>.asJsonRpcConnection(
    env: KsrpcEnvironment,
    includeContentHeaders: Boolean = true
): SingleChannelConnection {
    return JsonRpcWriterBase(
        CoroutineScope(coroutineContext),
        coroutineContext,
        env,
        if (includeContentHeaders) jsonHeader(env) else jsonLine(env)
    )
}
