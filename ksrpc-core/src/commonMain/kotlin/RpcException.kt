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
 * Wrapper around exceptions thrown in remote calls.
 *
 * Pins [code] to `-1` — a generic "remote error" classification. Typed
 * binding of error codes to `@Serializable` data classes via `@KsError`
 * will surface as bare [KsrpcException] instances with mapped `code`/`data`.
 */
class RpcException(message: String) : KsrpcException(code = -1, message = message)

/**
 * Build an [RpcException] carrying the stack captured in this [RpcFailure].
 */
fun RpcFailure.toException(): RpcException = RpcException(stack)
