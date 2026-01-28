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
    private val context: IrPluginContext,
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
