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
package com.monkopedia.ksrpc.jsonrpc

import com.monkopedia.ksrpc.channels.RpcCallId
import kotlinx.serialization.json.JsonPrimitive

/**
 * JSON-RPC [RpcCallId]: wraps the request's `id` primitive so cancellation can be routed by
 * the original request identifier.
 */
data class JsonRpcCallId(val id: JsonPrimitive) : RpcCallId

/**
 * Protocol-agnostic cancellation convention for JSON-RPC.
 *
 * JSON-RPC itself does not define cancellation semantics; individual protocols layer their
 * own convention on top (e.g. LSP uses `$/cancelRequest` with params `{ "id": <id> }`). The
 * ksrpc jsonrpc transport is configured with one of these to decide how to emit and interpret
 * the cancellation notification.
 *
 * The default is [None], which leaves cancellation a local-only operation (caller
 * `CancellationException` propagates, but nothing is sent to the remote side).
 */
sealed interface JsonRpcCancellationConvention {
    /** Disabled: no cancellation notification is sent or listened for. */
    data object None : JsonRpcCancellationConvention

    /**
     * Notification-based convention. The transport sends a JSON-RPC notification (no `id`)
     * with [method] and params `{ "id": <original request id> }` when a caller is cancelled.
     *
     * Incoming notifications with [method] are dispatched to cancel a previously registered
     * handler job.
     *
     * Matches LSP's `$/cancelRequest` shape when [method] is `"$/cancelRequest"`.
     */
    data class Notification(val method: String) : JsonRpcCancellationConvention

    companion object {
        /** Convenience: the Language Server Protocol's `$/cancelRequest` convention. */
        val Lsp: JsonRpcCancellationConvention = Notification("\$/cancelRequest")
    }
}
