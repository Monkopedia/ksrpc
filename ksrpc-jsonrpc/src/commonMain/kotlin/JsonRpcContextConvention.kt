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

/**
 * Convention for carrying `@KsContext` wire-context entries over JSON-RPC.
 *
 * JSON-RPC itself defines only `jsonrpc`, `method`, `params`, `id` (request) and
 * `jsonrpc`, `result`/`error`, `id` (response). This sealed interface lets the
 * application choose where context key-value pairs live on the wire.
 *
 * The default is [RootSiblings], which flat-merges context keys as root siblings
 * in the JSON-RPC request/response object.
 *
 * @see JsonRpcCancellationConvention for the analogous cancellation convention
 */
sealed interface JsonRpcContextConvention {
    /**
     * Disabled: no context entries are sent or read on the wire.
     */
    data object None : JsonRpcContextConvention

    /**
     * Delegate context propagation to the underlying HTTP transport headers.
     *
     * Only valid when the JSON-RPC connection is transported over HTTP.
     * Attempting to use this convention on a non-HTTP transport (e.g. stdio
     * or raw socket) will throw [IllegalStateException] at setup time.
     */
    data object TransportNative : JsonRpcContextConvention

    /**
     * Context keys are flat-merged as root-level siblings of `jsonrpc`, `method`,
     * `params`, and `id` in the JSON-RPC request/response objects.
     *
     * When this convention is selected, [validate] must be called at setup time
     * to ensure no binding's `wireKey` collides with reserved JSON-RPC fields.
     */
    data object RootSiblings : JsonRpcContextConvention

    /**
     * Context map is nested under a single root-level field in the JSON-RPC object.
     *
     * Example with `envelopeKey = "ctx"`:
     * ```json
     * { "jsonrpc": "2.0", "method": "foo", "params": ..., "id": 1, "ctx": { "x-auth": "token" } }
     * ```
     */
    data class RootField(val envelopeKey: String) : JsonRpcContextConvention

    /**
     * Context map is nested inside the `params` object under the given key.
     *
     * Example with `paramsKey = "_ctx"`:
     * ```json
     * { "jsonrpc": "2.0", "method": "foo", "params": { "input": ..., "_ctx": { "x-auth": "token" } }, "id": 1 }
     * ```
     */
    data class InParams(val paramsKey: String) : JsonRpcContextConvention

    companion object {
        /** Reserved JSON-RPC root-level field names that must not collide with wire keys. */
        val RESERVED_FIELDS: Set<String> = setOf(
            "jsonrpc",
            "method",
            "params",
            "id",
            "result",
            "error"
        )

        /**
         * Validates the given wire keys against the convention. For [RootSiblings],
         * throws [IllegalArgumentException] if any wireKey collides with a reserved
         * JSON-RPC field.
         */
        fun validate(convention: JsonRpcContextConvention, wireKeys: Collection<String>) {
            if (convention !is RootSiblings) return
            val collisions = wireKeys.filter { it in RESERVED_FIELDS }
            require(collisions.isEmpty()) {
                "JsonRpcContextConvention.RootSiblings: wireKey(s) $collisions collide " +
                    "with reserved JSON-RPC fields $RESERVED_FIELDS"
            }
        }
    }
}
