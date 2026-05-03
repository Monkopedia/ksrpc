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
@file:OptIn(KsrpcInternal::class)

package com.monkopedia.ksrpc.jsonrpc.internal

import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.ServiceTier
import com.monkopedia.ksrpc.annotation.KsrpcInternal
import com.monkopedia.ksrpc.channels.CancellationSupport
import com.monkopedia.ksrpc.channels.RpcCallId
import com.monkopedia.ksrpc.channels.SerializedService
import com.monkopedia.ksrpc.channels.SingleChannelConnection
import com.monkopedia.ksrpc.channels.WireContextMap
import com.monkopedia.ksrpc.channels.awaitRequestCancellable
import com.monkopedia.ksrpc.internal.MultiChannel
import com.monkopedia.ksrpc.jsonrpc.JsonRpcCallId
import com.monkopedia.ksrpc.jsonrpc.JsonRpcCancellationConvention
import com.monkopedia.ksrpc.jsonrpc.JsonRpcContextConvention
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

@KsrpcInternal
class JsonRpcWriterBase(
    private val scope: CoroutineScope,
    private val context: CoroutineContext,
    override val env: KsrpcEnvironment<String>,
    private val comm: JsonRpcTransformer,
    private val cancellationConvention: JsonRpcCancellationConvention =
        JsonRpcCancellationConvention.None,
    private val contextConvention: JsonRpcContextConvention =
        JsonRpcContextConvention.RootSiblings
) : JsonRpcChannel,
    SingleChannelConnection<String>,
    CancellationSupport {
    override val supportedTier: ServiceTier = ServiceTier.SIMPLE
    override val transportName: String = "JSON-RPC (SingleChannelConnection)"

    private val json = (env.serialization as? Json) ?: Json

    companion object {
        /**
         * Synthetic key used when [InParams] wraps a non-object params value so the
         * original can be recovered on the receiving side.
         */
        private const val IN_PARAMS_WRAPPED_VALUE_KEY = "__ksrpc_value"
    }

    private var baseChannel = CompletableDeferred<JsonRpcChannel>()
    private val multiChannel = MultiChannel<JsonRpcResponse>()
    private val handlers = mutableMapOf<JsonRpcCallId, Job>()

    init {
        scope.launch {
            withContext(context) {
                try {
                    while (comm.isOpen) {
                        val p = comm.receive() ?: continue
                        if ((p as? JsonObject)?.containsKey("method") == true) {
                            val wireCtx = extractContext(p)
                            val cleaned = stripContext(p)
                            val request = json.decodeFromJsonElement<JsonRpcRequest>(cleaned)
                            if (isCancellationNotification(request)) {
                                handleCancellationNotification(request)
                            } else {
                                launchRequestHandler(
                                    baseChannel.await(),
                                    request,
                                    wireCtx
                                )
                            }
                        } else {
                            val response = json.decodeFromJsonElement<JsonRpcResponse>(p)
                            multiChannel.send(response.id.toString(), response)
                        }
                    }
                } catch (t: Throwable) {
                    env.errorListener.onError(t)
                    try {
                        close()
                    } catch (t: Throwable) {
                    }
                }
            }
        }
    }

    private fun isCancellationNotification(request: JsonRpcRequest): Boolean {
        val convention = cancellationConvention as? JsonRpcCancellationConvention.Notification
            ?: return false
        return request.id == null && request.method == convention.method
    }

    private fun handleCancellationNotification(request: JsonRpcRequest) {
        val params = request.params as? JsonObject ?: return
        val id = params["id"] as? JsonPrimitive ?: return
        cancelHandler(JsonRpcCallId(id))
    }

    private fun launchRequestHandler(
        channel: JsonRpcChannel,
        message: JsonRpcRequest,
        wireCtx: WireContextMap?
    ) {
        val handlerContext = if (wireCtx != null) context + wireCtx else context
        scope.launch(handlerContext) {
            val id = message.id
            val callId = id?.let { JsonRpcCallId(it) }
            if (callId != null) {
                handlers[callId] = coroutineContext[Job]
                    ?: error("Handler launched without a Job in its context")
            }
            try {
                // Pass the request id through so the downstream SerializedService call can
                // hand it to RpcMethod.call, which installs the CurrentRpcCallElement at the
                // one central chokepoint.
                val response =
                    channel.execute(message.method, message.params, id == null, id)
                if (id == null) return@launch
                comm.send(
                    json.encodeToJsonElement(
                        JsonRpcResponse(
                            result = response,
                            id = id
                        )
                    )
                )
            } catch (t: CancellationException) {
                // Handler was cancelled (usually from a remote cancel notification). No
                // response is sent — jsonrpc cancellation conventions universally treat the
                // cancelled request as abandoned rather than completed.
                throw t
            } catch (t: JsonRpcServerError) {
                // Native JSON-RPC error envelope from the SerializedService layer (see
                // [JsonRpcServiceWrapper.execute]). Pass code / message / data through
                // unchanged — the typed `data` was already wire-encoded as a JsonElement.
                if (id != null) {
                    comm.send(
                        json.encodeToJsonElement(
                            JsonRpcResponse(
                                error = JsonRpcError(t.errorCode, t.message, t.data),
                                id = id
                            )
                        )
                    )
                }
            } catch (t: Throwable) {
                env.errorListener.onError(t)
                if (id != null) {
                    comm.send(
                        json.encodeToJsonElement(
                            JsonRpcResponse(
                                error = JsonRpcError(
                                    JsonRpcError.INTERNAL_ERROR,
                                    // Concise message only — full stack is logged via
                                    // env.errorListener above, not propagated to the peer.
                                    t.message ?: t.toString(),
                                    null
                                ),
                                id = id
                            )
                        )
                    )
                }
            } finally {
                if (callId != null) {
                    handlers.remove(callId)
                }
            }
        }
    }

    override suspend fun execute(
        method: String,
        message: JsonElement?,
        isNotify: Boolean,
        id: JsonPrimitive?
    ): JsonElement? {
        // The `id` parameter is only meaningful on the server side (forwarded from an inbound
        // request) — on the client/wire side we allocate our own wire id below. Accepting it
        // here keeps the interface symmetric; outbound calls pass null.
        val (wireId, pending) = if (isNotify) null to null else multiChannel.allocateReceive()
        val request = JsonRpcRequest(
            method = method,
            params = message,
            id = JsonPrimitive(wireId)
        )
        val wireCtx = coroutineContext[WireContextMap]
        val requestJson = injectContext(json.encodeToJsonElement(request), wireCtx, message)
        comm.send(requestJson)
        if (pending == null || wireId == null) return null
        val callId = JsonRpcCallId(JsonPrimitive(wireId))
        val response = try {
            awaitRequestCancellable(callId, pending)
        } catch (t: CancellationException) {
            // Drop the pending entry so a late response can't trip the "No pending receiver"
            // guard. The cleanup must run in NonCancellable scope because we're already on the
            // cancel path and Mutex.withLock would otherwise rethrow the CancellationException
            // immediately without dropping the entry.
            withContext(NonCancellable) {
                multiChannel.cancelPending(wireId.toString(), t)
            }
            throw t
        }
        if (response.error != null) {
            // Surface the native JSON-RPC error envelope to the caller so the routing
            // layer (RpcMethod.decodeError, after JsonRpcSerializedChannel translation)
            // can rebuild a typed Throwable from the @KsError forwardErrorMap.
            throw JsonRpcServerError(
                response.error.code,
                response.error.message,
                response.error.data
            )
        }
        return response.result
    }

    // region Context Convention Helpers

    /**
     * Injects [WireContextMap] entries into an outbound JSON-RPC request element
     * according to the configured [contextConvention].
     */
    private fun injectContext(
        requestJson: JsonElement,
        wireCtx: WireContextMap?,
        originalParams: JsonElement?
    ): JsonElement {
        if (wireCtx == null || wireCtx.values.isEmpty()) return requestJson
        if (contextConvention is JsonRpcContextConvention.None ||
            contextConvention is JsonRpcContextConvention.TransportNative
        ) {
            return requestJson
        }
        val obj = requestJson as? JsonObject ?: return requestJson
        val ctxObj = buildJsonObject {
            wireCtx.values.forEach { (k, v) -> put(k, JsonPrimitive(v)) }
        }
        return when (contextConvention) {
            is JsonRpcContextConvention.RootSiblings -> buildJsonObject {
                obj.forEach { (k, v) -> put(k, v) }
                wireCtx.values.forEach { (k, v) -> put(k, JsonPrimitive(v)) }
            }

            is JsonRpcContextConvention.RootField -> buildJsonObject {
                obj.forEach { (k, v) -> put(k, v) }
                put(contextConvention.envelopeKey, ctxObj)
            }

            is JsonRpcContextConvention.InParams -> {
                val params = obj["params"]
                val newParams = if (params is JsonObject) {
                    buildJsonObject {
                        params.forEach { (k, v) -> put(k, v) }
                        put(contextConvention.paramsKey, ctxObj)
                    }
                } else {
                    // params is not an object — wrap both value and context so we
                    // can recover the original on the receiving side.
                    buildJsonObject {
                        put(
                            IN_PARAMS_WRAPPED_VALUE_KEY,
                            params ?: kotlinx.serialization.json.JsonNull
                        )
                        put(contextConvention.paramsKey, ctxObj)
                    }
                }
                buildJsonObject {
                    obj.forEach { (k, v) ->
                        if (k == "params") put(k, newParams) else put(k, v)
                    }
                }
            }
        }
    }

    /**
     * Extracts [WireContextMap] from an inbound JSON-RPC request element
     * according to the configured [contextConvention]. Returns null if no
     * context entries are found or if the convention is [None]/[TransportNative].
     */
    private fun extractContext(obj: JsonObject): WireContextMap? {
        if (contextConvention is JsonRpcContextConvention.None ||
            contextConvention is JsonRpcContextConvention.TransportNative
        ) {
            return null
        }
        val map = mutableMapOf<String, String>()
        when (contextConvention) {
            is JsonRpcContextConvention.RootSiblings -> {
                // All keys not in the standard JSON-RPC set are context entries
                obj.forEach { (k, v) ->
                    if (k !in JsonRpcContextConvention.RESERVED_FIELDS) {
                        (v as? JsonPrimitive)?.contentOrNull?.let { map[k] = it }
                    }
                }
            }

            is JsonRpcContextConvention.RootField -> {
                val ctxObj = obj[contextConvention.envelopeKey] as? JsonObject ?: return null
                ctxObj.forEach { (k, v) ->
                    (v as? JsonPrimitive)?.contentOrNull?.let { map[k] = it }
                }
            }

            is JsonRpcContextConvention.InParams -> {
                val params = obj["params"] as? JsonObject ?: return null
                val ctxObj = params[contextConvention.paramsKey] as? JsonObject ?: return null
                ctxObj.forEach { (k, v) ->
                    (v as? JsonPrimitive)?.contentOrNull?.let { map[k] = it }
                }
            }
        }
        return if (map.isNotEmpty()) WireContextMap(map) else null
    }

    /**
     * Strips injected context entries from the raw JSON-RPC object so the
     * deserialized [JsonRpcRequest] sees only standard fields.
     */
    private fun stripContext(obj: JsonObject): JsonElement = when (contextConvention) {
        is JsonRpcContextConvention.RootSiblings -> {
            // Remove any non-standard keys that were context entries
            buildJsonObject {
                obj.forEach { (k, v) ->
                    if (k in JsonRpcContextConvention.RESERVED_FIELDS) put(k, v)
                }
            }
        }

        is JsonRpcContextConvention.RootField -> {
            buildJsonObject {
                obj.forEach { (k, v) ->
                    if (k != contextConvention.envelopeKey) put(k, v)
                }
            }
        }

        is JsonRpcContextConvention.InParams -> {
            val params = obj["params"] as? JsonObject
            if (params != null) {
                // Check if this was a wrapped non-object params
                val wrappedValue = params[IN_PARAMS_WRAPPED_VALUE_KEY]
                if (wrappedValue != null) {
                    // Unwrap: restore the original non-object params value
                    buildJsonObject {
                        obj.forEach { (k, v) ->
                            if (k == "params") put(k, wrappedValue) else put(k, v)
                        }
                    }
                } else {
                    // Normal object params: strip the context key
                    val cleanParams = buildJsonObject {
                        params.forEach { (k, v) ->
                            if (k != contextConvention.paramsKey) put(k, v)
                        }
                    }
                    buildJsonObject {
                        obj.forEach { (k, v) ->
                            if (k == "params") put(k, cleanParams) else put(k, v)
                        }
                    }
                }
            } else {
                obj
            }
        }

        else -> obj
    }

    // endregion

    override suspend fun close() {
        try {
            comm.close(IllegalStateException("JsonRpcWriter is shutting down"))
        } catch (t: IllegalStateException) {
            // Sometimes expected
        }
        try {
            multiChannel.close()
        } catch (t: Throwable) {
            // Thats fine, just pending messages getting unhappy.
        }
        val toCancel = handlers.values.toList()
        handlers.clear()
        toCancel.forEach { it.cancel(CancellationException("JsonRpcWriter closed")) }
    }

    override suspend fun registerDefault(service: SerializedService<String>) {
        baseChannel.complete(JsonRpcServiceWrapper(service))
    }

    override suspend fun defaultChannel(): SerializedService<String> =
        JsonRpcSerializedChannel(context, this, env)

    // region CancellationSupport

    override suspend fun sendCancel(callId: RpcCallId) {
        if (callId !is JsonRpcCallId) return
        val convention = cancellationConvention as? JsonRpcCancellationConvention.Notification
            ?: return
        try {
            comm.send(
                json.encodeToJsonElement(
                    JsonRpcRequest(
                        method = convention.method,
                        params = buildJsonObject { put("id", callId.id) },
                        id = null
                    )
                )
            )
        } catch (t: Throwable) {
            env.logger.debug("JsonRpcWriter", "Ignoring failure sending cancel notification", t)
        }
    }

    override fun registerHandler(callId: RpcCallId, job: Job) {
        if (callId !is JsonRpcCallId) return
        handlers[callId] = job
    }

    override fun unregisterHandler(callId: RpcCallId) {
        if (callId !is JsonRpcCallId) return
        handlers.remove(callId)
    }

    override fun cancelHandler(callId: RpcCallId, cause: CancellationException?) {
        if (callId !is JsonRpcCallId) return
        handlers.remove(callId)?.cancel(cause ?: CancellationException("Remote cancellation"))
    }

    // endregion
}
