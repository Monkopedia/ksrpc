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

package com.monkopedia.ksrpc

import com.monkopedia.ksrpc.annotation.KsrpcInternal
import com.monkopedia.ksrpc.channels.CallData
import com.monkopedia.ksrpc.channels.ChannelId
import com.monkopedia.ksrpc.channels.CurrentRpcCallElement
import com.monkopedia.ksrpc.channels.RpcBinaryData
import com.monkopedia.ksrpc.channels.RpcCallId
import com.monkopedia.ksrpc.channels.SerializedService
import com.monkopedia.ksrpc.channels.registerHost
import com.monkopedia.ksrpc.internal.ServiceExecutor
import com.monkopedia.ksrpc.internal.client
import com.monkopedia.ksrpc.internal.host
import com.monkopedia.ksrpc.internal.randomUuid
import kotlin.reflect.KClass
import kotlinx.coroutines.CancellationException
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

/**
 * Adapts a user-facing type to the wire-level [CallData] representation. Only
 * non-error variants flow through transformers — [CallData.Error] frames are
 * intercepted by [RpcMethod.callChannel] and converted to a thrown [Throwable]
 * via [RpcMethod.decodeError] before any transformer sees them.
 */
interface Transformer<T> {
    val hasContent: Boolean
        get() = true

    suspend fun <S> transform(input: T, channel: SerializedService<S>): CallData<S>
    suspend fun <S> untransform(data: CallData<S>, channel: SerializedService<S>): T
}

class SerializerTransformer<I>(val serializer: KSerializer<I>) : Transformer<I> {
    override val hasContent: Boolean
        get() = serializer != Unit.serializer()

    override suspend fun <T> transform(input: I, channel: SerializedService<T>): CallData<T> {
        channel.env.logger.debug("Transformer", "Serializing input to CallData")
        return channel.env.serialization.createCallData(serializer, input)
    }

    override suspend fun <T> untransform(data: CallData<T>, channel: SerializedService<T>): I {
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
        channel.env.logger.debug("Transformer", "Deserializing CallData to RpcBinaryData")
        return data.readBinary()
    }
}

/**
 * Shared abstract base for transformers whose wire representation is a
 * sub-service reference (a channel id registered on the host, resolved to a
 * client stub on the peer). The base holds the register / lookup machinery
 * operating on the service-facing type [T]; concrete subclasses adapt a
 * (possibly different) user-facing type [O] to [T] via [toService] /
 * [fromService].
 *
 * Introspection consumers pattern-match on this base class to recover the
 * underlying sub-service name regardless of the user-facing adapter shape —
 * e.g. `SubserviceTransformer<T>` (trivial `O == T`) and
 * `com.monkopedia.ksrpc.flow.FlowSubserviceTransformer<T>` (adapts
 * `Flow<T>` onto `KsFlowService<T>`) both surface via the same branch.
 */
@KsrpcInternal
abstract class BaseSubserviceTransformer<T : RpcService, O> : Transformer<O> {
    abstract val serviceObject: RpcObject<T>

    protected abstract fun toService(value: O): T
    protected abstract fun fromService(service: T): O

    override suspend fun <S> transform(input: O, channel: SerializedService<S>): CallData<S> {
        val service = toService(input)
        val host = host<S>() ?: error("Cannot transform service type to non-hosting channel")
        val serviceId = host.registerHost(service, serviceObject)
        channel.env.logger.info("Transformer", "Serializing Service to CallData(${serviceId.id})")
        return channel.env.serialization.createCallData(String.serializer(), serviceId.id)
    }

    override suspend fun <S> untransform(data: CallData<S>, channel: SerializedService<S>): O {
        val client = client<S>() ?: error("Cannot untransform service type from non-client channel")
        val serviceId = channel.env.serialization.decodeCallData(String.serializer(), data)
        channel.env.logger.info("Transformer", "Deserializing CallData($serviceId) to Stub")
        val service = serviceObject.createStub(client.wrapChannel(ChannelId(serviceId)))
        return fromService(service)
    }
}

@OptIn(KsrpcInternal::class)
class SubserviceTransformer<T : RpcService>(
    override val serviceObject: RpcObject<T>
) : BaseSubserviceTransformer<T, T>() {
    override fun toService(value: T): T = value
    override fun fromService(service: T): T = service
}

/**
 * Describes one `@KsError(code, type)` binding captured by the ksrpc compiler
 * plugin on a `@KsMethod` function. The plugin emits one entry per annotation
 * in declaration order into [RpcMethod.errorMappings]. Runtime routing (#78)
 * consumes these via the [forwardErrorMap] / [reverseErrorMap] helpers on
 * [RpcMethod] — forward maps for client-side deserialization by incoming wire
 * code, reverse maps for server-side resolution of a thrown `data::class` back
 * to its code + serializer.
 */
class KsErrorMapping(val code: Int, val dataType: KClass<*>, val dataSerializer: KSerializer<*>)

/**
 * Forward lookup built from [RpcMethod.errorMappings]: code → mapping. Used by
 * the client-side error-decoder to resolve an incoming wire-level code back to
 * the captured [KSerializer] for deserializing the payload. Entries from later
 * `@KsError` annotations that share a code overwrite earlier ones; duplicate
 * codes on a single method are a programming error that transport validation
 * can surface.
 */
val RpcMethod<*, *, *>.forwardErrorMap: Map<Int, KsErrorMapping>
    get() = errorMappings.associateBy { it.code }

/**
 * Reverse lookup built from [RpcMethod.errorMappings]: `data::class` → mapping.
 * Used by the server-side error-encoder to resolve the thrown `data::class`
 * back to its bound code + captured [KSerializer]. Later entries overwrite
 * earlier ones for the same type.
 */
val RpcMethod<*, *, *>.reverseErrorMap: Map<KClass<*>, KsErrorMapping>
    get() = errorMappings.associateBy { it.dataType }

/**
 * A wrapper around calling into or from stubs/serialization.
 *
 * The optional [metadata] list carries sibling-annotation metadata captured by
 * the ksrpc compiler plugin from annotations on the source method that are
 * themselves annotated `@KsMethodMetadata`. Transport layers can read it to
 * customize how a call is serialized.
 *
 * The optional [errorMappings] list carries `@KsError(code, type)` bindings
 * captured by the compiler plugin on the source method. Each entry pairs a
 * wire-level integer code with the `@Serializable` payload type and its
 * [KSerializer]. Runtime routing (see [forwardErrorMap] / [reverseErrorMap])
 * uses these to translate between thrown exception data and wire-level error
 * envelopes.
 */
class RpcMethod<T : RpcService, I, O>(
    val endpoint: String,
    val inputTransform: Transformer<I>,
    val outputTransform: Transformer<O>,
    private val method: ServiceExecutor,
    val metadata: List<MethodMetadata>,
    val errorMappings: List<KsErrorMapping> = emptyList()
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
            try {
                val transformedInput = inputTransform.untransform(input, channel)
                val logId = randomUuid()
                channel.env.logger.info("Transformer", "($logId) Calling endpoint $endpoint")
                val output = method.invoke(service as T, transformedInput)
                channel.env.logger.debug("Transformer", "($logId) Completed endpoint $endpoint")
                outputTransform.transform(output as O, channel)
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                channel.env.logger.info(
                    "RpcMethod",
                    "Exception thrown from endpoint $endpoint",
                    t
                )
                channel.env.errorListener.onError(t)
                encodeError(t, channel.env)
            }
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
                if (transformedOutput is CallData.Error<S>) {
                    val decoded = decodeError(transformedOutput, channel.env)
                    channel.env.logger.info(
                        "RpcMethod",
                        "Decoded error response from endpoint $endpoint",
                        decoded
                    )
                    throw decoded
                }
                outputTransform.untransform(transformedOutput, channel)
            }
            if (timeout != null && timeout > 0) {
                withTimeout(timeout) { body() }
            } else {
                body()
            }
        }
    }

    /**
     * Server-side: convert a thrown exception into a [CallData.Error] using
     * this method's [reverseErrorMap]. The thrown class IS the wire payload's
     * class — when the throwable's runtime class is assignable to a
     * `@KsError`-bound type, the bound code + serializer are used and the
     * throwable itself is encoded into the wire format `S` for inclusion in
     * the error frame. There is no `KsrpcException` wrapper involved on the
     * server side; users `throw MyTypedError(...)` directly.
     *
     * The `@KsError(code, ...)` binding's `code` is the single source of
     * truth for the wire code — there is no per-throw override mechanism.
     * Callers that want a different code for the same data should bind it
     * differently. Falls back to sentinel-coded built-in errors:
     * [RpcEndpointException] -> [KsrpcException.ENDPOINT_NOT_FOUND_CODE],
     * a thrown [KsrpcException] without a binding -> its own
     * [KsrpcException.code], any other Throwable ->
     * [KsrpcException.INTERNAL_ERROR_CODE].
     */
    @Suppress("UNCHECKED_CAST")
    fun <S> encodeError(t: Throwable, env: KsrpcEnvironment<S>): CallData.Error<S> {
        // Walk errorMappings in declaration order, picking the first binding whose
        // dataType is assignable from the throwable's runtime class. This mirrors
        // normal `catch (e: BaseError)` semantics — a binding for a base type catches
        // subclass instances. Declaration order serves as precedence when multiple
        // bindings could match.
        val mapping = errorMappings.firstOrNull { it.dataType.isInstance(t) }
        val payloadT: S? = mapping?.let { m ->
            runCatching {
                val ser = m.dataSerializer as KSerializer<Any?>
                (env.serialization.createCallData(ser, t) as CallData.Serialized<S>)
                    .readSerialized()
            }.getOrNull()
        }
        val code = when {
            mapping != null -> mapping.code
            t is RpcEndpointException -> KsrpcException.ENDPOINT_NOT_FOUND_CODE
            t is KsrpcException -> t.code
            else -> KsrpcException.INTERNAL_ERROR_CODE
        }
        return CallData.Error(code, t.message ?: t.toString(), payloadT)
    }

    /**
     * Client-side: convert a [CallData.Error] into a [Throwable] using this
     * method's [forwardErrorMap]. When [CallData.Error.errorCode] matches a
     * `@KsError` binding and [CallData.Error.errorData] decodes successfully
     * with the bound serializer, the deserialized typed Throwable itself is
     * returned (and re-thrown by [callChannel]) — callers `catch (e: MyError)`
     * typed.
     *
     * For built-in sentinels the result is [RpcEndpointException]
     * ([KsrpcException.ENDPOINT_NOT_FOUND_CODE]) or [RpcException]
     * ([KsrpcException.INTERNAL_ERROR_CODE]).
     *
     * Forward-compat: any other unknown wire code (e.g. a newer server's typed
     * error not bound on this client) surfaces a generic [KsrpcException]
     * carrying the raw wire-format payload bytes as [KsrpcException.data], so
     * callers can still inspect the payload manually rather than losing the
     * information.
     */
    @Suppress("UNCHECKED_CAST")
    fun <S> decodeError(error: CallData.Error<S>, env: KsrpcEnvironment<S>): Throwable {
        val mapping = forwardErrorMap[error.errorCode]
        val typed = if (mapping != null && error.errorData != null) {
            runCatching {
                val ser = mapping.dataSerializer as KSerializer<Any?>
                env.serialization.decodeCallData(
                    ser,
                    CallData.Serialized<S>(error.errorData)
                ) as? Throwable
            }.getOrNull()
        } else {
            null
        }
        if (typed != null) return typed
        return when (error.errorCode) {
            KsrpcException.ENDPOINT_NOT_FOUND_CODE -> RpcEndpointException(error.errorMessage)
            KsrpcException.INTERNAL_ERROR_CODE -> RpcException(error.errorMessage)
            // Forward-compat: unknown wire code (e.g. newer server's typed error not
            // bound here) — surface a generic KsrpcException carrying the raw
            // wire-format payload so callers can still inspect the data even without
            // the @KsError binding.
            else -> KsrpcException(
                code = error.errorCode,
                message = error.errorMessage,
                data = error.errorData
            )
        }
    }

    @OptIn(KsrpcInternal::class)
    fun findSubserviceTransformers(): List<BaseSubserviceTransformer<*, *>> =
        listOfNotNull(
            inputTransform as? BaseSubserviceTransformer<*, *>,
            outputTransform as? BaseSubserviceTransformer<*, *>
        )
}
