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

import kotlinx.serialization.Serializable

/**
 * Serializable wrapper around exceptions thrown in remote calls.
 */
@Serializable
data class RpcFailure(val stack: String) {
    /**
     * Converts this failure to an [RpcException] (which extends
     * [KsrpcException]). Transports that carry richer error codes (e.g.
     * JSON-RPC) should construct [KsrpcException] directly with the wire code.
     */
    fun toException(): RpcException = RpcException(stack)
}

/**
 * Wrapper around exceptions thrown in remote calls.
 *
 * Retained for backward compatibility; new code should catch
 * [KsrpcException] instead.
 */
class RpcException(override val message: String) :
    KsrpcException(
        code = -1,
        message = message
    )
