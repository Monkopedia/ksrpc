/*
 * Copyright (C) 2026 Jason Monk <monkopedia@gmail.com>
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
package com.monkopedia.ksrpc.webworker.test

import com.monkopedia.ksrpc.IntrospectableRpcService
import com.monkopedia.ksrpc.annotation.KsIntrospectable
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService

@KsService
@KsIntrospectable
interface WebWorkerTestService : IntrospectableRpcService {
    @KsMethod("/ping")
    suspend fun ping(input: String): String

    @KsMethod("/rpc")
    suspend fun rpc(u: Pair<String, String>): String

    @KsMethod("/service")
    suspend fun subservice(prefix: String): WebWorkerTestSubService
}

@KsService
@KsIntrospectable
interface WebWorkerTestSubService : IntrospectableRpcService {
    @KsMethod("/rpc")
    suspend fun rpc(u: Pair<String, String>): String
}

class WebWorkerTestServiceImpl(private val pongSuffix: String) : WebWorkerTestService {
    override suspend fun ping(input: String): String = "pong:$input:$pongSuffix"

    override suspend fun rpc(u: Pair<String, String>): String = "${u.first} ${u.second}"

    override suspend fun subservice(prefix: String): WebWorkerTestSubService =
        object : WebWorkerTestSubService {
            override suspend fun rpc(u: Pair<String, String>): String =
                "$prefix ${u.first} ${u.second}"
        }
}
