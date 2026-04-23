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
 * Base class for exceptions thrown by the ksrpc runtime.
 *
 * Carries an integer [code] classifying the error, a human-readable [message]
 * for wire-format compatibility and fallback debugging, and an optional typed
 * [data] payload. When a method declares `@KsError` bindings, the runtime
 * deserializes the wire payload into the mapped `@Serializable` type and
 * exposes it here; callers that handle a known error type down-cast [data] to
 * inspect it, while callers without a typed mapping observe `data == null`
 * and rely on [code] + [message].
 *
 * Subclasses may narrow the conventions (e.g. [RpcException] pins `code = -1`,
 * [RpcEndpointException] pins `code = -32601`), and transports translate
 * [code] into their native error envelope.
 */
open class KsrpcException(
    val code: Int,
    override val message: String,
    val data: Any? = null,
    cause: Throwable? = null
) : RuntimeException(message, cause)
