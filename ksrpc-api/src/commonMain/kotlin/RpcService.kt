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
package com.monkopedia.ksrpc

/**
 * Super-interface of all services tagged with [com.monkopedia.ksrpc.annotation.KsService].
 *
 * Plain [RpcService] supports only simple input-to-output methods. For services
 * that return sub-services, extend [RpcHostService]; for services that accept
 * sub-service inputs or use `Flow<T>`, extend [RpcBidiService].
 */
interface RpcService : SuspendCloseable {
    override suspend fun close() = Unit
}

/**
 * A service whose methods may return other `@KsService` sub-services.
 *
 * Extend this instead of [RpcService] when any `@KsMethod` returns a type
 * annotated with `@KsService`. The compiler plugin enforces this at compile
 * time and produces a clear error if the wrong tier is used.
 *
 * HTTP transports can host [RpcHostService] instances (sub-service outputs are
 * multiplexed over the same connection), but cannot host [RpcBidiService].
 */
interface RpcHostService : RpcService

/**
 * A service whose methods accept sub-service inputs, use `Flow<T>`, or
 * otherwise require bidirectional transport capability.
 *
 * Extend this instead of [RpcHostService] when any `@KsMethod` accepts a
 * `@KsService` type as input, or uses `Flow<T>` (which internally bridges
 * through a bidirectional sub-service protocol).
 *
 * Only bidirectional transports (WebSocket, raw sockets, JNI) can host
 * [RpcBidiService] instances.
 */
interface RpcBidiService : RpcHostService
