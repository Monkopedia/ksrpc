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
@file:Suppress("unused", "UNUSED_VARIABLE")

package com.monkopedia.ksrpc.samples

import com.monkopedia.ksrpc.RpcService
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.flow.asKsFlow
import com.monkopedia.ksrpc.ksrpcEnvironment
import com.monkopedia.ksrpc.serialized
import com.monkopedia.ksrpc.toStub
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList

// ---- Service with Flow methods ----

@KsService
interface LogService : RpcService {
    @KsMethod("/stream_logs")
    suspend fun streamLogs(filter: String): Flow<String>
}

// ---- Sample functions ----

/**
 * Demonstrates declaring a service method that returns a `Flow<T>`.
 */
fun flowMethodDeclaration() {
    // A @KsMethod can return Flow<T>. The compiler plugin bridges the flow
    // over the sub-service protocol using KsFlowService internally.
    // The caller collects the flow using standard coroutines APIs.
}

/**
 * Demonstrates collecting a flow returned from a service method.
 */
suspend fun collectingFlowFromService() {
    val service = object : LogService {
        override suspend fun streamLogs(filter: String): Flow<String> =
            flow {
                emit("[$filter] Starting...")
                emit("[$filter] Processing...")
                emit("[$filter] Done.")
            }
    }

    val env = ksrpcEnvironment { }
    val serialized = service.serialized(env)
    val stub = serialized.toStub<LogService, String>()

    // Collect the flow -- the framework handles the sub-service lifecycle.
    val logs = stub.streamLogs("error").toList()
    // logs == ["[error] Starting...", "[error] Processing...", "[error] Done."]
}
