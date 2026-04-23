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
package com.monkopedia.ksrpc.internal

import com.monkopedia.ksrpc.RpcService
import com.monkopedia.ksrpc.annotation.KsrpcInternal

/**
 * Plumbing interface emitted by the ksrpc compiler plugin to adapt reflective
 * invocation of generated service implementations. Not part of the supported
 * API surface — user code should never implement or directly call this, and
 * the plugin emits the implementation automatically.
 */
@KsrpcInternal
interface ServiceExecutor {
    suspend fun invoke(service: RpcService, input: Any?): Any?
}
