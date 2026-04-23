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
import com.monkopedia.ksrpc.RpcFailure
import com.monkopedia.ksrpc.annotation.KsrpcInternal
import com.monkopedia.ksrpc.asString
import com.monkopedia.ksrpc.channels.CancellationSupport
import com.monkopedia.ksrpc.channels.RpcCallId
import com.monkopedia.ksrpc.channels.SerializedService
import com.monkopedia.ksrpc.channels.SingleChannelConnection
import com.monkopedia.ksrpc.channels.awaitRequestCancellable
import com.monkopedia.ksrpc.internal.MultiChannel
import com.monkopedia.ksrpc.jsonrpc.JsonRpcCallId
import com.monkopedia.ksrpc.jsonrpc.JsonRpcCancellationConvention
import com.monkopedia.ksrpc.toException
import kotlin.coroutines.CoroutineContext
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
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

@KsrpcInternal
class JsonRpcWriterBase(
    private val scope: CoroutineScope,
    private val context: CoroutineContext,
    override val env: KsrpcEnvironment<String>,
    private val comm: JsonRpcTransformer,
    private val cancellationConvention: JsonRpcCancellationConvention =
        JsonRpcCancellationConvention.None
) : JsonRpcChannel,
    SingleChannelConnection<String>,
    CancellationSupport {
    private val json = (env.serialization as? Json) ?: Json

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
                            val request = json.decodeFromJsonElement<JsonRpcRequest>(p)
                            if (isCancellationNotification(request)) {
                                handleCancellationNotification(request)
                            } else {
                                launchRequestHandler(baseChannel.await(), request)
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

    private fun launchRequestHandler(channel: JsonRpcChannel, message: JsonRpcRequest) {
        scope.launch(context) {
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
            } catch (t: Throwable) {
                env.errorListener.onError(t)
                if (id != null) {
                    comm.send(
                        json.encodeToJsonElement(
                            JsonRpcResponse(
                                error = JsonRpcError(
                                    JsonRpcError.INTERNAL_ERROR,
                                    t.asString,
                                    json.encodeToJsonElement(RpcFailure(t.asString))
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
        comm.send(json.encodeToJsonElement(request))
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
            val error = response.error.data?.let {
                try {
                    json.decodeFromJsonElement<RpcFailure>(it).toException()
                } catch (_: Throwable) {
                    null
                }
            } ?: IllegalStateException(
                "JsonRpcError(${response.error.code}): ${response.error.message}"
            )
            throw error
        }
        return response.result
    }

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
