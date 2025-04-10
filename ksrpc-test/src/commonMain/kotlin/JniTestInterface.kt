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

import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import io.ktor.utils.io.ByteReadChannel

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
