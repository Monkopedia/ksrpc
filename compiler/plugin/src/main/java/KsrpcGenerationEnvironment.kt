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
@file:OptIn(UnsafeDuringIrConstructionAPI::class)

package com.monkopedia.ksrpc.plugin

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

class KsrpcGenerationEnvironment(
    val context: IrPluginContext,
    // Retained so future bootstrap lookups can surface warnings through the
    // plugin's MessageCollector without restructuring callers.
    @Suppress("unused") private val messageCollector: MessageCollector
) {
    private val endpointException =
        referenceClass(FqConstants.RPC_ENDPOINT_EXCEPTION)
    val endpointStrConstructor = endpointException.constructors.first()
    val rpcService = referenceClass(FqConstants.RPC_SERVICE)
    val serializedService = referenceClass(FqConstants.SERIALIZED_SERVICE)
    val rpcMethod = referenceClass(FqConstants.RPC_METHOD)
    val serviceExecutor = referenceClass(FqConstants.SERVICE_EXECUTOR)
    val serializerTransformer = referenceClass(FqConstants.SERIALIZER_TRANSFORMER)

    /**
     * Registry of binary adapters the plugin knows about. Each entry pairs a
     * user-facing binary type (seen in an `@KsMethod` signature) with the
     * adapter `Transformer` object that lives in a dedicated `ksrpc-binary-*`
     * module. Adapter modules are optional on the compile classpath: a
     * consumer that never declares one of these types in a service signature
     * does not need the corresponding adapter jar. Call sites that depend on
     * a specific adapter look it up via [findAdapterForUserType] /
     * [findAdapterByFqName] and emit a user-error diagnostic pointing at
     * [BinaryAdapter.moduleHint] when the adapter is missing.
     *
     * Adding a new binary adapter is a matter of adding a new entry here and
     * the matching pair of FQN constants in [FqConstants] — no per-type
     * branches elsewhere in the plugin.
     */
    internal val binaryAdapters: List<BinaryAdapter> = listOf(
        BinaryAdapter(
            userFqName = FqConstants.BYTE_READ_CHANNEL,
            transformerFqName = FqConstants.BYTE_READ_CHANNEL_TRANSFORMER,
            moduleHint = "ksrpc-binary-ktor",
            transformerClass = maybeReferenceClass(FqConstants.BYTE_READ_CHANNEL_TRANSFORMER)
        ),
        BinaryAdapter(
            userFqName = FqConstants.KOTLINX_IO_SOURCE,
            transformerFqName = FqConstants.SOURCE_TRANSFORMER,
            moduleHint = "ksrpc-binary-kotlinx-io",
            transformerClass = maybeReferenceClass(FqConstants.SOURCE_TRANSFORMER)
        ),
        BinaryAdapter(
            userFqName = FqConstants.OKIO_BUFFERED_SOURCE,
            transformerFqName = FqConstants.BUFFERED_SOURCE_TRANSFORMER,
            moduleHint = "ksrpc-binary-okio",
            transformerClass = maybeReferenceClass(FqConstants.BUFFERED_SOURCE_TRANSFORMER)
        )
    )

    /** FQN lookup used by method-shape validation and dispatcher paths. */
    internal val binaryUserFqNames: Set<FqName> = binaryAdapters.map { it.userFqName }.toSet()

    /** Find the adapter for a user-facing [FqName], or null if unknown. */
    internal fun findAdapterByFqName(fqName: FqName?): BinaryAdapter? =
        fqName?.let { name -> binaryAdapters.firstOrNull { it.userFqName == name } }
    val introspectionImpl: IrClassSymbol by lazy {
        referenceClass(FqConstants.INTROSPECTION_SERVICE_IMPL)
    }
    val introspectionConstructor: IrConstructorSymbol by lazy {
        introspectionImpl.constructors.first()
    }
    val serviceTierClass = referenceClass(FqConstants.SERVICE_TIER_CLASS)
    val subserviceTransformer = referenceClass(FqConstants.SUBSERVICE_TRANSFORMER)
    val rpcObjectKey = maybeReferenceClass(FqConstants.RPC_OBJECT_KEY)
    val suspendCloseable = referenceClass(FqConstants.SUSPEND_CLOSEABLE)

    val kSerializer = referenceClass(FqConstants.KSERIALIZER)
    val serializerMethod =
        context.referenceFunctions(FqConstants.SERIALIZER_CALLABLE).find {
            it.owner.dispatchReceiverParameter == null &&
                it.owner.typeParameters.size == 1 &&
                it.owner.parameters.isEmpty()
        }
    val resolveSerializerOrThrow =
        context.referenceFunctions(FqConstants.RESOLVE_SERIALIZER_OR_THROW).firstOrNull()

    // `val <T : Any> KSerializer<T>.nullable: KSerializer<T?>` — an extension property
    // declared in `kotlinx.serialization.builtins.BuiltinSerializers`. We resolve the
    // property symbol here and use its getter for IR-time composition of nullable
    // serializers.
    val getSerializerNullable =
        context.referenceProperties(
            CallableId(
                FqName("kotlinx.serialization.builtins"),
                Name.identifier("nullable")
            )
        ).firstOrNull { prop ->
            prop.owner.getter?.parameters?.any {
                it.kind == IrParameterKind.ExtensionReceiver
            } == true
        }?.owner?.getter?.symbol

    // Top-level builder functions in `kotlinx.serialization.builtins` that compose
    // element serializers into `KSerializer<List<T>>` / `KSerializer<Set<T>>` /
    // `KSerializer<Map<K, V>>`. Used by the generic @KsService codegen to build
    // composed serializer expressions for wrapper types that reference a class-level
    // type parameter. Resolved optimistically — callers fall back to reportUserError
    // when the lookup misses (should only happen on a broken kotlinx-serialization
    // classpath).
    val listSerializerBuilder =
        context.referenceFunctions(
            CallableId(
                FqName("kotlinx.serialization.builtins"),
                Name.identifier("ListSerializer")
            )
        ).firstOrNull { fn ->
            fn.owner.parameters.count { it.kind == IrParameterKind.Regular } == 1 &&
                fn.owner.typeParameters.size == 1
        }
    val setSerializerBuilder =
        context.referenceFunctions(
            CallableId(
                FqName("kotlinx.serialization.builtins"),
                Name.identifier("SetSerializer")
            )
        ).firstOrNull { fn ->
            fn.owner.parameters.count { it.kind == IrParameterKind.Regular } == 1 &&
                fn.owner.typeParameters.size == 1
        }
    val mapSerializerBuilder =
        context.referenceFunctions(
            CallableId(
                FqName("kotlinx.serialization.builtins"),
                Name.identifier("MapSerializer")
            )
        ).firstOrNull { fn ->
            fn.owner.parameters.count { it.kind == IrParameterKind.Regular } == 2 &&
                fn.owner.typeParameters.size == 2
        }

    // ksrpc-flow runtime types — optional. When unresolved (ksrpc-flow not on the
    // compile classpath), the plugin does not auto-wire `Flow<T>` and the
    // surrounding error for a missing serializer will fire on the user code as
    // usual. `flowSupported` gates the detection path.
    val ksFlowService = maybeReferenceClass(FqConstants.KS_FLOW_SERVICE)
    val flowTransformer = maybeReferenceClass(FqConstants.FLOW_TRANSFORMER)

    /** True when `Flow<T>` signatures can be auto-wired via the ksrpc-flow runtime. */
    val flowSupported: Boolean = ksFlowService != null && flowTransformer != null

    val threadLocal = referenceClass(FqConstants.THREAD_LOCAL)
    val listOfFunction =
        context.referenceFunctions(
            CallableId(FqName("kotlin.collections"), Name.identifier("listOf"))
        ).firstOrNull { fn ->
            val regularParams = fn.owner.parameters.filter { it.kind == IrParameterKind.Regular }
            regularParams.size == 1 && regularParams.single().varargElementType != null
        }
            ?: reportInternal("can't resolve kotlin.collections.listOf (vararg overload)")

    val emptyListFunction =
        context.referenceFunctions(
            CallableId(FqName("kotlin.collections"), Name.identifier("emptyList"))
        ).firstOrNull()
            ?: reportInternal("can't resolve kotlin.collections.emptyList")

    // Error-binding (#77) support is optional so the compiler plugin continues
    // to work against older ksrpc-core artifacts that predate the `KsErrorMapping`
    // type. When unresolved, the plugin does not emit the seventh constructor
    // argument on `RpcMethod` and the generated service carries no error maps.
    val ksErrorMapping = maybeReferenceClass(FqConstants.KS_ERROR_MAPPING)

    /**
     * True when `KsErrorMapping` was resolved — the ksrpc-core on the compile
     * classpath supports the `@KsError` bidirectional binding (Part 2 of #13).
     * Also requires the 6-argument `RpcMethod` constructor.
     */
    val errorMappingSupported: Boolean get() = ksErrorMapping != null &&
        rpcMethod.constructors.first().owner.parameters.size >= 6

    // Context-binding (#81) support is optional so the compiler plugin continues
    // to work against older ksrpc-core artifacts that predate the `KsContextMapping`
    // type. When unresolved, the plugin does not emit the eighth constructor
    // argument on `RpcMethod` and the generated service carries no context bindings.
    val ksContextMapping = maybeReferenceClass(FqConstants.KS_CONTEXT_MAPPING)

    /**
     * True when `KsContextMapping` was resolved — the ksrpc-core on the compile
     * classpath supports the `@KsContext` context propagation (issue #81).
     * Also requires the 7-argument `RpcMethod` constructor.
     */
    val contextBindingSupported: Boolean get() = ksContextMapping != null &&
        rpcMethod.constructors.first().owner.parameters.size >= 7

    // Metadata propagation support is optional so the compiler plugin continues
    // to work against older ksrpc-core artifacts that predate #11.
    val methodMetadata = maybeReferenceClass(FqConstants.METHOD_METADATA)
    val metadataValue = maybeReferenceClass(FqConstants.METADATA_VALUE)
    val metadataValueString = maybeReferenceClass(FqConstants.METADATA_VALUE_STRING)
    val metadataValueInt = maybeReferenceClass(FqConstants.METADATA_VALUE_INT)
    val metadataValueLong = maybeReferenceClass(FqConstants.METADATA_VALUE_LONG)
    val metadataValueBoolean = maybeReferenceClass(FqConstants.METADATA_VALUE_BOOLEAN)
    val metadataValueDouble = maybeReferenceClass(FqConstants.METADATA_VALUE_DOUBLE)
    val metadataValueFloat = maybeReferenceClass(FqConstants.METADATA_VALUE_FLOAT)
    val metadataValueKClass = maybeReferenceClass(FqConstants.METADATA_VALUE_KCLASS)
    val metadataValueEnum = maybeReferenceClass(FqConstants.METADATA_VALUE_ENUM)
    val metadataValueList = maybeReferenceClass(FqConstants.METADATA_VALUE_LIST)
    val pair = maybeReferenceClass(FqConstants.PAIR)
    val toFunction =
        context.referenceFunctions(FqConstants.TO_FUNCTION).firstOrNull { fn ->
            fn.owner.parameters.any { it.kind == IrParameterKind.ExtensionReceiver } &&
                fn.owner.parameters.count { it.kind == IrParameterKind.Regular } == 1
        }

    /**
     * True when every symbol needed to emit `MethodMetadata` entries was
     * resolved. When false, the plugin passes `null` (falling back to the old
     * four-arg constructor) and does not attempt metadata propagation.
     */
    val metadataSupported: Boolean =
        methodMetadata != null &&
            metadataValue != null &&
            metadataValueString != null &&
            metadataValueInt != null &&
            metadataValueLong != null &&
            metadataValueBoolean != null &&
            metadataValueDouble != null &&
            metadataValueFloat != null &&
            metadataValueKClass != null &&
            metadataValueEnum != null &&
            metadataValueList != null &&
            pair != null &&
            toFunction != null

    private fun maybeReferenceClass(name: ClassId): IrClassSymbol? = context.referenceClass(name)

    private fun referenceClass(name: ClassId): IrClassSymbol =
        maybeReferenceClass(name) ?: reportInternal(
            "can't resolve $name on the compile classpath — ksrpc-core must be a dependency"
        )
}

/**
 * One entry in the plugin's binary-adapter registry.
 *
 * @property userFqName     User-facing type FQN (e.g. `io.ktor.utils.io.ByteReadChannel`).
 * @property transformerFqName `Transformer` implementation FQN in the adapter module
 *                          (e.g. `com.monkopedia.ksrpc.binary.ktor.ByteReadChannelTransformer`).
 * @property moduleHint     The gradle-coordinate name of the adapter module, surfaced in the
 *                          missing-on-classpath diagnostic (e.g. `"ksrpc-binary-ktor"`).
 * @property transformerClass Resolved symbol for the transformer, or `null` when the adapter
 *                          module is not on the compile classpath. Call sites check this
 *                          and emit a user diagnostic pointing at [moduleHint].
 */
internal data class BinaryAdapter(
    val userFqName: FqName,
    val transformerFqName: ClassId,
    val moduleHint: String,
    val transformerClass: IrClassSymbol?
)
