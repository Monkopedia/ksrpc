/**
 * Copyright (C) 2024 Jason Monk <monkopedia@gmail.com>
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
package com.monkopedia.ksrpc.plugin

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.jvm.ir.erasedUpperBound
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.builders.IrBlockBodyBuilder
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.IrGeneratorContext
import org.jetbrains.kotlin.ir.builders.Scope
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.builders.declarations.addTypeParameter
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrBranch
import org.jetbrains.kotlin.ir.expressions.IrClassReference
import org.jetbrains.kotlin.ir.expressions.IrElseBranch
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrWhen
import org.jetbrains.kotlin.ir.expressions.impl.IrClassReferenceImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrElseBranchImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrWhenImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.starProjectedType
import org.jetbrains.kotlin.ir.util.SYNTHETIC_OFFSET
import org.jetbrains.kotlin.ir.util.allOverridden
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.util.parentClassOrNull
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

class KsrpcGenerationEnvironment(
    val pluginContext: IrPluginContext,
    val messageCollector: MessageCollector,
    val clsMgr: ClassGenerationManager
) {
    val illegalArgument = referenceClass(FqConstants.RPC_ENDPOINT_EXCEPTION)
    val illegalArgumentStrConstructor = illegalArgument.constructors.first()
    val rpcObject = referenceClass(FqConstants.RPC_OBJECT)
    val rpcService = referenceClass(FqConstants.RPC_SERVICE)
    val serializedService = referenceClass(FqConstants.SERIALIZED_SERVICE)
    val rpcMethod = referenceClass(FqConstants.RPC_METHOD)
    val serviceExecutor = referenceClass(FqConstants.SERVICE_EXECUTOR)
    val serializerTransformer = referenceClass(FqConstants.SERIALIZER_TRANSFORMER)
    val binaryTransformer = referenceObject(FqConstants.BINARY_TRANSFORMER)
    val subserviceTransformer = referenceClass(FqConstants.SUBSERVICE_TRANSFORMER)
    val rpcObjectKey = maybeReferenceClass(FqConstants.RPC_OBJECT_KEY)
    val suspendCloseable = referenceClass(FqConstants.SUSPEND_CLOSEABLE)

    val kSerializer = referenceClass(FqConstants.KSERIALIZER)
    val serializerMethod = pluginContext.referenceFunctions(FqConstants.SERIALIZER_CALLABLE).find {
        it.owner.dispatchReceiverParameter == null &&
            it.owner.extensionReceiverParameter == null &&
            it.owner.typeParameters.size == 1 &&
            it.owner.valueParameters.isEmpty()
    }

    val threadLocal = referenceClass(FqConstants.THREAD_LOCAL)

    val byteReadChannel = FqName(FqConstants.BYTE_READ_CHANNEL)

    private fun maybeReferenceClass(name: String): IrClassSymbol? {
        val fqName = ClassId.fromString(name)
        return pluginContext.referenceClass(fqName)
    }

    private fun referenceClass(name: String): IrClassSymbol {
        return maybeReferenceClass(name)
            ?: run {
                messageCollector.report(
                    CompilerMessageSeverity.ERROR,
                    "Can't find $name in dependencies"
                )
                error("Can't find $name in dependencies")
            }
    }

    private fun referenceObject(name: String): IrClassSymbol {
        val fqName = ClassId.fromString(name)
        return pluginContext.referenceClass(fqName)
            ?: run {
                messageCollector.report(
                    CompilerMessageSeverity.ERROR,
                    "Can't find $name in dependencies"
                )
                error("Can't find $name in dependencies")
            }
    }

    fun IrPluginContext.createIrBuilder(symbol: IrSymbol) =
        DeclarationIrBuilder(
            this,
            symbol,
            symbol.owner.startOffset.takeIf { it != 0 }
                ?: SYNTHETIC_OFFSET,
            symbol.owner.endOffset.takeIf { it != 0 }
                ?: SYNTHETIC_OFFSET
        )

    fun createClassReference(irClass: IrClass, startOffset: Int, endOffset: Int): IrClassReference {
        val classType = irClass.defaultType
        val classSymbol = irClass.symbol
        return IrClassReferenceImpl(
            startOffset,
            endOffset,
            pluginContext.irBuiltIns.kClassClass.starProjectedType,
            classSymbol,
            classType
        )
    }

    inline fun IrClass.overrideMethod(
        method: IrSimpleFunctionSymbol,
        returnType: IrType = method.owner.returnType,
        body: IrBlockBodyBuilder.(IrSimpleFunction) -> Unit
    ) = addFunction(
        method.owner.name.asString(),
        returnType,
        Modality.FINAL,
        method.owner.visibility,
        false,
        method.owner.isSuspend,
        false,
        startOffset = SYNTHETIC_OFFSET,
        endOffset = SYNTHETIC_OFFSET
    ).apply {
        val baseFqName = method.owner.parentAsClass.fqNameWhenAvailable!!
        val allTypes = (superTypes.mapNotNull { it.classOrNull?.owner } + this@overrideMethod)
        overriddenSymbols = allTypes.mapNotNull { superType ->
            superType?.takeIf { superClass ->
                superClass.isSubclassOfFqName(
                    baseFqName.asString()
                )
            }
        }.flatMap { superClass ->
            superClass.functions.filter { function ->
                function.name.asString() == method.owner.name.asString() &&
                    function.overridesFunctionIn(baseFqName)
            }.map { it.symbol }.toList()
        }
        method.owner.typeParameters.map {
            addTypeParameter {
                startOffset = SYNTHETIC_OFFSET
                endOffset = SYNTHETIC_OFFSET
                this.index = it.index
                this.isReified = it.isReified
                this.superTypes.addAll(it.superTypes)
                this.variance = it.variance
                this.name = it.name
            }
        }
        method.owner.valueParameters.map {
            addValueParameter {
                startOffset = SYNTHETIC_OFFSET
                endOffset = SYNTHETIC_OFFSET
                name = it.name
                type = it.type
                index = it.index
                isHidden = it.isHidden
                isAssignable = it.isAssignable
                isCrossInline = it.isCrossinline
                varargElementType = it.varargElementType
            }
        }
        this.body = pluginContext.createIrBuilder(symbol).irBlockBody {
            body(this@apply)
        }
    }

    fun IrClass.isSubclassOfFqName(fqName: String): Boolean =
        fqNameWhenAvailable?.asString() == fqName || superTypes.any {
            it.erasedUpperBound.isSubclassOfFqName(
                fqName
            )
        }

    fun IrSimpleFunction.overridesFunctionIn(fqName: FqName): Boolean =
        parentClassOrNull?.fqNameWhenAvailable == fqName ||
            allOverridden().any { it.parentClassOrNull?.fqNameWhenAvailable == fqName }

    fun IrClass.build(exec: IrClass.() -> Unit): IrClass = with(clsMgr) {
        build(exec)
    }

    class BranchBuilder(
        private val irWhen: IrWhen,
        context: IrGeneratorContext,
        scope: Scope,
        startOffset: Int,
        endOffset: Int
    ) : IrBuilderWithScope(context, scope, startOffset, endOffset) {
        operator fun IrBranch.unaryPlus() {
            irWhen.branches.add(this)
        }
    }

    fun IrBuilderWithScope.irWhen(
        typeHint: IrType? = null,
        block: BranchBuilder.() -> Unit
    ): IrWhen {
        val whenExpr = IrWhenImpl(
            startOffset,
            endOffset,
            typeHint
                ?: pluginContext.irBuiltIns.unitType
        )
        val builder = BranchBuilder(whenExpr, context, scope, startOffset, endOffset)
        builder.block()
        return whenExpr
    }

    fun BranchBuilder.elseBranch(result: IrExpression): IrElseBranch =
        IrElseBranchImpl(
            IrConstImpl.boolean(
                result.startOffset,
                result.endOffset,
                pluginContext.irBuiltIns.booleanType,
                true
            ),
            result
        )

    companion object {

        fun create(
            pluginContext: IrPluginContext,
            messageCollector: MessageCollector,
            cls: (KsrpcGenerationEnvironment) -> Unit
        ) {
            ClassGenerationManager.buildClasses { clsMgr ->
                val manager = KsrpcGenerationEnvironment(pluginContext, messageCollector, clsMgr)
                cls(manager)
            }
        }
    }
}
