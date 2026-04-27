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
package com.monkopedia.ksrpc.ktor

import com.monkopedia.ksrpc.KsrpcException

/**
 * Header carrying the original ksrpc error code when the wire status maps to the default
 * 500 fallback (i.e. the code is not present in the configured `errorCodeToHttpStatus`
 * map). The client uses this to recover the exact code for `@KsError`-bound payload routing
 * on the receive path. Match this constant on both ends — see also the duplicate definition
 * in `ksrpc-ktor-server`'s `HttpStream.kt`.
 */
const val KSRPC_ERROR_CODE_HEADER: String = "X-Ksrpc-Error-Code"

/**
 * Header carrying the human-readable error message that pairs with the ksrpc error code on
 * the wire. The body slot carries the typed `errorData` payload; the message moves to a
 * header so it survives transports that strip/escape the body, and so the client can
 * recover it cleanly even when the body decode fails.
 */
const val KSRPC_ERROR_MESSAGE_HEADER: String = "X-Ksrpc-Error-Message"

/**
 * Default mapping from ksrpc error codes to HTTP status codes used by the HTTP transport.
 *   - [KsrpcException.ENDPOINT_NOT_FOUND_CODE] (`-32601`) -> 404
 *   - [KsrpcException.INTERNAL_ERROR_CODE] (`-32603`) -> 500
 *
 * Codes not present in the configured map default to status 500, with the original code
 * carried in [KSRPC_ERROR_CODE_HEADER]. The error response body always carries the
 * wire-format-encoded error payload (or empty when no payload is attached).
 *
 * Pass the same map on both ends so the round-trip preserves user-defined codes.
 */
val DEFAULT_KSRPC_ERROR_CODE_TO_HTTP_STATUS: Map<Int, Int> = mapOf(
    KsrpcException.ENDPOINT_NOT_FOUND_CODE to 404,
    KsrpcException.INTERNAL_ERROR_CODE to 500
)
