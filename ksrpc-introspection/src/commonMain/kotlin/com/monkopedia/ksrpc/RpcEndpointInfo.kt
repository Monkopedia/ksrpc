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

import com.monkopedia.ksrpc.annotation.KsrpcInternal
import kotlin.collections.associate
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind

@Serializable
data class RpcDescriptor(
    val dataType: RpcDescriptorType,
    val serialName: String,
    val elements: Map<String, RpcDescriptor> = emptyMap(),
    /**
     * This is a unique identifier only present in the case where the same type appears
     * multiple times in the same [RpcDescriptor] hierarchy.
     *
     * When the [id] is present, only one of the first instance of the [RpcDescriptor] will have
     * the [elements] populated, all other instances will be empty, which allows for recursive
     * types.
     *
     * Note that [id]s do not have a guaranteed base index or continuous, they are simply used for
     * correlation across instances.
     */
    val id: Int? = null
)

/**
 * Metadata about the RPC-level representations of inputs/outputs.
 */
@Serializable
sealed class RpcDataType {

    @Serializable
    data class DataStructure(val schema: RpcDescriptor) : RpcDataType() {
        constructor(serializer: KSerializer<*>) : this(dataSchema(serializer))
    }

    @Serializable
    object BinaryData : RpcDataType()

    @Serializable
    data class Service(
        val qualifiedName: String,
        val typeArgs: List<RpcDataType> = emptyList()
    ) : RpcDataType()
}

/**
 * Introspection payload describing a single endpoint.
 */
@Serializable
data class RpcEndpointInfo(val endpoint: String, val input: RpcDataType, val output: RpcDataType)

fun dataSchema(serializer: KSerializer<*>): RpcDescriptor =
    dataSchema(serializer.descriptor).invoke()

private fun dataSchema(
    descriptor: SerialDescriptor,
    idLookup: MutableMap<SerialDescriptor, Pair<Boolean, Int>> = mutableMapOf()
): () -> RpcDescriptor {
    val state = idLookup[descriptor]
    if (state == null) {
        idLookup[descriptor] = false to idLookup.size
    } else if (!state.first) {
        idLookup[descriptor] = true to state.second
    }
    val children = state?.let {
        emptyMap()
    } ?: (0 until descriptor.elementsCount).associate { index ->
        descriptor.getElementName(index) to
            dataSchema(descriptor.getElementDescriptor(index), idLookup)
    }
    return {
        val id = idLookup[descriptor]?.takeIf { it.first }?.second
        RpcDescriptor(
            dataType = descriptor.kind.rpcType,
            serialName = descriptor.serialName,
            id = id,
            elements = children.mapValues { it.value.invoke() }
        )
    }
}

enum class RpcDescriptorType {
    OPEN,
    SEALED,
    CONTEXTUAL,
    ENUM,
    BOOLEAN,
    BYTE,
    CHAR,
    DOUBLE,
    FLOAT,
    INT,
    LONG,
    SHORT,
    STRING,
    CLASS,
    LIST,
    MAP,
    OBJECT,
    UNKNOWN
}

@OptIn(ExperimentalSerializationApi::class)
@Suppress("REDUNDANT_ELSE_IN_WHEN")
private val SerialKind.rpcType: RpcDescriptorType
    get() = when (this) {
        PolymorphicKind.OPEN -> RpcDescriptorType.OPEN
        PolymorphicKind.SEALED -> RpcDescriptorType.SEALED
        SerialKind.CONTEXTUAL -> RpcDescriptorType.CONTEXTUAL
        SerialKind.ENUM -> RpcDescriptorType.ENUM
        PrimitiveKind.BOOLEAN -> RpcDescriptorType.BOOLEAN
        PrimitiveKind.BYTE -> RpcDescriptorType.BYTE
        PrimitiveKind.CHAR -> RpcDescriptorType.CHAR
        PrimitiveKind.DOUBLE -> RpcDescriptorType.DOUBLE
        PrimitiveKind.FLOAT -> RpcDescriptorType.FLOAT
        PrimitiveKind.INT -> RpcDescriptorType.INT
        PrimitiveKind.LONG -> RpcDescriptorType.LONG
        PrimitiveKind.SHORT -> RpcDescriptorType.SHORT
        PrimitiveKind.STRING -> RpcDescriptorType.STRING
        StructureKind.CLASS -> RpcDescriptorType.CLASS
        StructureKind.LIST -> RpcDescriptorType.LIST
        StructureKind.MAP -> RpcDescriptorType.MAP
        StructureKind.OBJECT -> RpcDescriptorType.OBJECT
        else -> RpcDescriptorType.UNKNOWN
    }

internal fun RpcMethod<*, *, *>.inputRpcDataType(): RpcDataType = inputTransform.rpcDataType

internal fun RpcMethod<*, *, *>.outputRpcDataType(): RpcDataType = outputTransform.rpcDataType

@OptIn(KsrpcInternal::class)
internal val Transformer<*>.rpcDataType: RpcDataType
    get() = when (this) {
        is BinaryDataTransformer -> RpcDataType.BinaryData

        is SerializerTransformer<*> -> RpcDataType.DataStructure(serializer)

        // Matches `SubserviceTransformer<T>` (trivial adapter) as well as any
        // user-facing adapter built on the same base — e.g. ksrpc-flow's
        // `FlowSubserviceTransformer<T>` which adapts `Flow<T>` onto a
        // `KsFlowService<T>` sub-service. All such transformers expose the
        // underlying `RpcObject<T>` via `serviceObject`, so introspection can
        // recover the real sub-service name regardless of the user-facing
        // wrapper shape. Type arguments (if any) are derived from the
        // transformer's `typeArgSerializers` so callers can see the full
        // parameterization (e.g. `KsFlowService<Update>`).
        is BaseSubserviceTransformer<*, *> -> RpcDataType.Service(
            qualifiedName = serviceObject.serviceName,
            typeArgs = typeArgSerializers.map { RpcDataType.DataStructure(it) }
        )

        // Truly unknown transformer — fall back to the simple class name so
        // at least something is reported. Callers will not be able to
        // resolve it via `getIntrospectionFor`, but that's the best we can
        // do without an in-tree bridge.
        else -> RpcDataType.Service(this::class.simpleName ?: "Unknown")
    }
