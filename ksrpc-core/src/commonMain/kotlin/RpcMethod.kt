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

import com.monkopedia.ksrpc.channels.CallData
import com.monkopedia.ksrpc.channels.ChannelId
import com.monkopedia.ksrpc.channels.CurrentRpcCallElement
import com.monkopedia.ksrpc.channels.RpcBinaryData
import com.monkopedia.ksrpc.channels.RpcCallId
import com.monkopedia.ksrpc.channels.SerializedService
import com.monkopedia.ksrpc.channels.randomUuid
import com.monkopedia.ksrpc.channels.registerHost
import com.monkopedia.ksrpc.internal.client
import com.monkopedia.ksrpc.internal.host
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer

private const val KS_TIMEOUT_FQ = "com.monkopedia.ksrpc.annotation.KsTimeout"

/**
 * Returns the timeout in milliseconds configured via `@KsTimeout` on the
 * source method, or `null` if no timeout annotation was present.
 *
 * The total is computed as `millis + seconds * 1000 + minutes * 60_000`.
 */
val RpcMethod<*, *, *>.timeoutMillis: Long?
    get() {
        val meta = metadata(KS_TIMEOUT_FQ) ?: return null
        val millis = (meta.argument("millis") as? MetadataValue.LongValue)?.value ?: 0L
        val seconds = (meta.argument("seconds") as? MetadataValue.LongValue)?.value ?: 0L
        val minutes = (meta.argument("minutes") as? MetadataValue.LongValue)?.value ?: 0L
        return millis + seconds * 1000 + minutes * 60_000
    }

interface Transformer<T> {
    val hasContent: Boolean
        get() = true

    suspend fun <S> transform(input: T, channel: SerializedService<S>): CallData<S>
    suspend fun <S> untransform(data: CallData<S>, channel: SerializedService<S>): T

    fun <S> unpackError(data: CallData<S>, channel: SerializedService<S>) {
        if (!data.isBinary && channel.env.serialization.isError(data)) {
            throw channel.env.serialization.decodeErrorCallData(data).also {
                channel.env.logger.info("Transformer", "Decoding Throwable form CallData", it)
            }
        }
    }
}

class SerializerTransformer<I>(val serializer: KSerializer<I>) : Transformer<I> {
    override val hasContent: Boolean
        get() = serializer != Unit.serializer()

    override suspend fun <T> transform(input: I, channel: SerializedService<T>): CallData<T> {
        channel.env.logger.debug("Transformer", "Serializing input to CallData")
        return channel.env.serialization.createCallData(serializer, input)
    }

    override suspend fun <T> untransform(data: CallData<T>, channel: SerializedService<T>): I {
        unpackError(data, channel)
        channel.env.logger.debug("Transformer", "Deserializing CallData to type")
        return channel.env.serialization.decodeCallData(serializer, data)
    }
}

/**
 * Marker for transformers whose wire representation is `CallData.Binary` /
 * [RpcBinaryData], regardless of the user-facing type they adapt. Consumers
 * (e.g. introspection, diagnostics) should treat any implementor as "binary
 * payload" rather than enumerating individual adapter objects.
 */
interface BinaryDataTransformer

object BinaryTransformer :
    Transformer<RpcBinaryData>,
    BinaryDataTransformer {
    override suspend fun <T> transform(
        input: RpcBinaryData,
        channel: SerializedService<T>
    ): CallData<T> {
        channel.env.logger.debug("Transformer", "Serializing RpcBinaryData to CallData")
        return CallData.createBinary(input)
    }

    override suspend fun <T> untransform(
        data: CallData<T>,
        channel: SerializedService<T>
    ): RpcBinaryData {
        unpackError(data, channel)
        channel.env.logger.debug("Transformer", "Deserializing CallData to RpcBinaryData")
        return data.readBinary()
    }
}

class SubserviceTransformer<T : RpcService>(private val serviceObj: RpcObject<T>) : Transformer<T> {
    val serviceObject: RpcObject<T>
        get() = serviceObj
    override suspend fun <S> transform(input: T, channel: SerializedService<S>): CallData<S> {
        val host = host<S>() ?: error("Cannot transform service type to non-hosting channel")
        val serviceId = host.registerHost(input, serviceObj)
        channel.env.logger.info("Transformer", "Serializing Service to CallData(${serviceId.id})")
        return channel.env.serialization.createCallData(String.serializer(), serviceId.id)
    }

    override suspend fun <S> untransform(data: CallData<S>, channel: SerializedService<S>): T {
        val client = client<S>() ?: error("Cannot untransform service type from non-client channel")
        unpackError(data, channel)
        val serviceId = channel.env.serialization.decodeCallData(String.serializer(), data)
        channel.env.logger.info("Transformer", "Deserializing CallData($serviceId) to Stub")
        return serviceObj.createStub(client.wrapChannel(ChannelId(serviceId)))
    }
}

interface ServiceExecutor {
    suspend fun invoke(service: RpcService, input: Any?): Any?
}

/**
 * A wrapper around calling into or from stubs/serialization.
 *
 * The optional [metadata] list carries sibling-annotation metadata captured by
 * the ksrpc compiler plugin from annotations on the source method that are
 * themselves annotated `@KsMethodMetadata`. Transport layers can read it to
 * customize how a call is serialized.
 */
class RpcMethod<T : RpcService, I, O>(
    val endpoint: String,
    val inputTransform: Transformer<I>,
    val outputTransform: Transformer<O>,
    private val method: ServiceExecutor,
    val metadata: List<MethodMetadata>
) {

    val hasReturnType: Boolean
        get() = outputTransform.hasContent

    /**
     * Look up the captured sibling-annotation metadata with the given fully
     * qualified annotation name, or null if the method was not annotated with
     * such a sibling annotation.
     */
    fun metadata(annotationFqName: String): MethodMetadata? =
        metadata.firstOrNull { it.annotationFqName == annotationFqName }

    @Suppress("UNCHECKED_CAST")
    suspend fun <S> call(
        channel: SerializedService<S>,
        service: RpcService,
        input: CallData<S>,
        callId: RpcCallId?
    ): CallData<S> {
        // Install CurrentRpcCallElement at the one place handlers are actually invoked. This
        // is the central chokepoint — transports do not install the element themselves, they
        // just pass the id through. Nested outbound calls that re-enter RpcMethod.call on the
        // far side naturally get a fresh element installed, so no caller-side stripping is
        // needed.
        val currentCall = CurrentRpcCallElement(method = this, id = callId)
        return withContext(channel.context.minusKey(Job) + currentCall) {
            val transformedInput = inputTransform.untransform(input, channel)
            val logId = randomUuid()
            channel.env.logger.info("Transformer", "($logId) Calling endpoint $endpoint")
            val output = method.invoke(service as T, transformedInput)
            channel.env.logger.debug("Transformer", "($logId) Completed endpoint $endpoint")
            outputTransform.transform(output as O, channel)
        }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun <S> callChannel(channel: SerializedService<S>, input: Any?): Any? {
        val timeout = timeoutMillis
        // Drop the Job element from the channel's context so the caller's Job remains the
        // parent for cancellation propagation — otherwise cancelling the caller wouldn't
        // surface inside the remote call (it would only cancel this [withContext] body's
        // own child scope, which is unreachable from the caller's cancel signal).
        return withContext(channel.context.minusKey(Job)) {
            val body: suspend () -> Any? = {
                val input = inputTransform.transform(input as I, channel)
                val id = randomUuid()
                channel.env.logger.info(
                    "Transformer",
                    "($id) Calling remote endpoint $endpoint"
                )
                // Client-side stubs do not forward a callId — they generate their own
                // wire-level id at the transport if needed, and any server-side handler
                // reached via the remote transport will have its own
                // CurrentRpcCallElement installed by that transport's RpcMethod.call
                // invocation.
                val transformedOutput = channel.call(this@RpcMethod, input, callId = null)
                channel.env.logger.debug(
                    "Transformer",
                    "($id) Completed remote endpoint $endpoint"
                )
                outputTransform.untransform(transformedOutput, channel)
            }
            if (timeout != null && timeout > 0) {
                withTimeout(timeout) { body() }
            } else {
                body()
            }
        }
    }

    fun findSubserviceTransformers(): List<SubserviceTransformer<out RpcService>> = listOfNotNull(
        inputTransform as? SubserviceTransformer<*>,
        outputTransform as? SubserviceTransformer<*>
    )
}
