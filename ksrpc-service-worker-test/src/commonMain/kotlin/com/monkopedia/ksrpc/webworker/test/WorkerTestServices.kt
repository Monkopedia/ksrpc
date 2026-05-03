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

import com.monkopedia.ksrpc.RpcHostService
import com.monkopedia.ksrpc.RpcService
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService

/**
 * Mirror of `TestInterface` from ksrpc-test. Defined here because the worker
 * runs in a separate JS bundle that cannot access test source sets.
 * The wire contract (endpoint name, serialization) must match exactly.
 */
@KsService
interface WorkerTestInterface : RpcService {
    @KsMethod("/rpc")
    suspend fun rpc(u: Pair<String, String>): String
}

/**
 * Mirror of `TestSubInterface` from ksrpc-test.
 */
@KsService
interface WorkerTestSubInterface : RpcService {
    @KsMethod("/rpc")
    suspend fun rpc(u: Pair<String, String>): String
}

/**
 * Mirror of `TestRootInterface` from ksrpc-test.
 */
@KsService
interface WorkerTestRootInterface : RpcHostService {
    @KsMethod("/rpc")
    suspend fun rpc(u: Pair<String, String>): String

    @KsMethod("/service")
    suspend fun subservice(prefix: String): WorkerTestSubInterface
}
