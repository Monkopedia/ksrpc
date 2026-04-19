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
 * Base exception for errors originating from a remote RPC call.
 *
 * This is the standard type that clients should catch when a remote method
 * returns an error. It carries a numeric [code] (transport-defined, e.g.
 * JSON-RPC error codes), a human-readable [message], and an optional [data]
 * payload serialized as a plain string.
 *
 * The [data] field is intentionally a [String] (the serialized form of the
 * error payload) so that ksrpc-core remains transport-agnostic: no
 * `JsonElement` or `Any?` leaks into the core type hierarchy. Transport
 * layers that support typed error data (e.g. JSON-RPC with `@KsErrorData`)
 * decode the string into the declared type on the client side.
 *
 * @property code transport-level error code (e.g. JSON-RPC `-32603`).
 * @property data serialized error payload, or `null` if no structured data
 *   was attached to the error.
 */
open class KsrpcException(
    val code: Int,
    override val message: String,
    val data: String? = null,
    cause: Throwable? = null
) : RuntimeException(message, cause) {
    override fun toString(): String = "KsrpcException(code=$code, message=$message, data=$data)"
}
