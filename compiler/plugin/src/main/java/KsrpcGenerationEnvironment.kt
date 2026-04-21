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
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity.ERROR
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
    private val messageCollector: MessageCollector
) {
    private val endpointException =
        referenceClass(FqConstants.RPC_ENDPOINT_EXCEPTION)
    val endpointStrConstructor = endpointException.constructors.first()
    val rpcService = referenceClass(FqConstants.RPC_SERVICE)
    val serializedService = referenceClass(FqConstants.SERIALIZED_SERVICE)
    val rpcMethod = referenceClass(FqConstants.RPC_METHOD)
    val serviceExecutor = referenceClass(FqConstants.SERVICE_EXECUTOR)
    val serializerTransformer = referenceClass(FqConstants.SERIALIZER_TRANSFORMER)
    val binaryTransformer = referenceObject(FqConstants.BINARY_TRANSFORMER)
    val introspectionImpl: IrClassSymbol by lazy {
        referenceClass(FqConstants.INTROSPECTION_SERVICE_IMPL)
    }
    val introspectionConstructor: IrConstructorSymbol by lazy {
        introspectionImpl.constructors.first()
    }
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

    val threadLocal = referenceClass(FqConstants.THREAD_LOCAL)
    val listOfFunction =
        context.referenceFunctions(
            CallableId(FqName("kotlin.collections"), Name.identifier("listOf"))
        ).firstOrNull { fn ->
            val regularParams = fn.owner.parameters.filter { it.kind == IrParameterKind.Regular }
            regularParams.size == 1 && regularParams.single().varargElementType != null
        }
            ?: run {
                messageCollector.report(ERROR, "Can't find kotlin.collections.listOf")
                error("Can't find kotlin.collections.listOf")
            }

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

    private fun referenceClass(name: ClassId): IrClassSymbol = maybeReferenceClass(name) ?: run {
        messageCollector.report(ERROR, "Can't find $name in dependencies")
        error("Can't find $name in dependencies")
    }

    private fun referenceObject(name: ClassId): IrClassSymbol =
        context.referenceClass(name) ?: run {
            messageCollector.report(ERROR, "Can't find $name in dependencies")
            error("Can't find $name in dependencies")
        }
}
