/*
 * Copyright 2021 Jason Monk
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

import org.jetbrains.kotlin.backend.common.ir.addChild
import org.jetbrains.kotlin.backend.common.ir.createImplicitParameterDeclarationWithWrappedDescriptor
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.common.lower.irIfThen
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.builders.declarations.addConstructor
import org.jetbrains.kotlin.ir.builders.declarations.addDispatchReceiver
import org.jetbrains.kotlin.ir.builders.declarations.addField
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irDelegatingConstructorCall
import org.jetbrains.kotlin.ir.builders.irEqualsNull
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irImplicitCast
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irSetField
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.getClass
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.types.starProjectedType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.SYNTHETIC_OFFSET
import org.jetbrains.kotlin.ir.util.companionObject
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.util.superTypes
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.util.capitalizeDecapitalize.capitalizeAsciiOnly

object StubGeneration {
    fun generate(
        cls: ServiceClass,
        clsType: IrSimpleType,
        env: KsrpcGenerationEnvironment
    ): Pair<IrClass, Map<String, IrFunction>> = with(env) {
        val methods = mutableMapOf<String, IrFunction>()
        val stubCls = pluginContext.irFactory.buildClass {
            this.startOffset = SYNTHETIC_OFFSET
            this.endOffset = SYNTHETIC_OFFSET
            this.name = Name.identifier("${cls.irClass.name.asString()}Stub")
            this.modality = Modality.FINAL
            this.visibility = DescriptorVisibilities.PUBLIC
            this.isCompanion = false
            this.kind = ClassKind.CLASS
        }.apply {
            this.parent = cls.irClass
            cls.irClass.addChild(this)
        }.build {
            origin = IrDeclarationOrigin.DELEGATE
            superTypes = listOf(clsType, env.rpcService.typeWith())
            createImplicitParameterDeclarationWithWrappedDescriptor()
            val field = addField {
                name = Name.identifier("channel")
                type = env.serializedChannel.typeWith()
                visibility = DescriptorVisibilities.PRIVATE
                origin = IrDeclarationOrigin.DELEGATE
                isFinal = true
                startOffset = SYNTHETIC_OFFSET
                endOffset = SYNTHETIC_OFFSET
            }
            val thisAsReceiverParameter = thisReceiver!!
            addConstructor {
                isPrimary = true
                startOffset = SYNTHETIC_OFFSET
                endOffset = SYNTHETIC_OFFSET
            }.apply {
                val parameter = addValueParameter {
                    name = Name.identifier("channel")
                    type = env.serializedChannel.typeWith()
                    startOffset = SYNTHETIC_OFFSET
                    endOffset = SYNTHETIC_OFFSET
                }
                body = pluginContext.createIrBuilder(symbol).irSynthBody {
                    +irDelegatingConstructorCall(
                        context.irBuiltIns.anyClass.owner.constructors.single()
                    )
                    +irSetField(irGet(thisAsReceiverParameter), field, irGet(parameter))
                }
            }

            val companion = pluginContext.irFactory.buildClass {
                this.startOffset = SYNTHETIC_OFFSET
                this.endOffset = SYNTHETIC_OFFSET
                this.name = Name.identifier("Companion")
                this.modality = Modality.FINAL
                this.visibility = DescriptorVisibilities.PUBLIC
                this.isCompanion = true
                this.kind = ClassKind.OBJECT
            }.apply {
                this.parent = this@build
                origin = IrDeclarationOrigin.DELEGATE
                annotations = listOf(
                    pluginContext.createIrBuilder(symbol).irCallConstructor(
                        threadLocal.constructors.single(),
                        listOf()
                    )
                )
                createImplicitParameterDeclarationWithWrappedDescriptor()
                addConstructor {
                    startOffset = SYNTHETIC_OFFSET
                    endOffset = SYNTHETIC_OFFSET
                    isPrimary = true
                }.apply {
                    body = pluginContext.createIrBuilder(symbol).irBlockBody {
                        +irDelegatingConstructorCall(
                            context.irBuiltIns.anyClass.owner.constructors.single()
                        )
                    }
                }
                this@build.addChild(this)
            }
            for ((method, annotation) in cls.methods) {
                val endpoint = (annotation.getValueArgument(0) as? IrConst<String>)?.value
                    ?: error("Lost endpoint")
                val methodField = generateRpcMethod(
                    env,
                    endpoint,
                    method,
                    this,
                    cls.irClass,
                    companion
                )
                methods[endpoint] = methodField
                val callChannel = rpcMethod.functions.find {
                    it.owner.name.asString() == FqConstants.CALL_CHANNEL
                }
                overrideMethod(method.symbol) { override ->
                    +irReturn(
                        irCall(callChannel!!).apply {
                            this.type = method.returnType
                            dispatchReceiver = irCall(methodField).apply {
                                dispatchReceiver = irGetObject(companion.symbol)
                            }
                            putValueArgument(
                                0,
                                irGetField(irGet(override.dispatchReceiverParameter!!), field)
                            )
                            if (override.valueParameters.size != 1) {
                                val overrideParams = override.valueParameters.map {
                                    it.name.asString() to it.type.classFqName?.asString()
                                }
                                error(
                                    "Unexpected override parameters $overrideParams"
                                )
                            }
                            putValueArgument(1, irGet(override.valueParameters[0]))
                        }
                    )
                }
            }
        }
        return stubCls to methods
    }

    private fun generateRpcMethod(
        env: KsrpcGenerationEnvironment,
        endpoint: String,
        method: IrSimpleFunction,
        stubCls: IrClass,
        serviceInterface: IrClass,
        companion: IrClass
    ): IrFunction = with(env) {
        return with(companion) {
            val inputType = method.valueParameters[0].type
            val outputType = method.returnType
            val type = env.determineType(method, inputType, outputType)
            val field = addField {
                startOffset = SYNTHETIC_OFFSET
                endOffset = SYNTHETIC_OFFSET
                this.name = Name.identifier(
                    method.name.asString().capitalizeAsciiOnly() + "Backing"
                )
                origin = IrDeclarationOrigin.DELEGATE
                this.visibility = DescriptorVisibilities.INTERNAL
                this.type = rpcMethod.starProjectedType.makeNullable()
                this.isFinal = false
                this.isStatic = false
            }
            addFunction {
                startOffset = SYNTHETIC_OFFSET
                endOffset = SYNTHETIC_OFFSET
                this.name = Name.identifier(method.name.asString().capitalizeAsciiOnly())
                this.origin = IrDeclarationOrigin.DELEGATE
                this.visibility = DescriptorVisibilities.INTERNAL
                this.returnType = rpcMethod.starProjectedType
            }.apply {
                addDispatchReceiver {
                    this.type = companion.typeWith()
                }
                body = pluginContext.createIrBuilder(symbol).irBlockBody {
                    +irIfThen(
                        irEqualsNull(irGetField(irGet(dispatchReceiverParameter!!), field)),
                        irSetField(
                            irGet(dispatchReceiverParameter!!),
                            field,
                            irCreateRpcMethod(
                                this@apply,
                                serviceInterface,
                                inputType,
                                outputType,
                                endpoint,
                                type,
                                this@apply,
                                method
                            )
                        )
                    )
                    +irReturn(irGetField(irGet(dispatchReceiverParameter!!), field))
                }
            }
        }
    }

    private fun KsrpcGenerationEnvironment.irCreateRpcMethod(
        irField: IrFunction,
        serviceInterface: IrClass,
        inputType: IrType,
        outputType: IrType,
        endpoint: String,
        type: RpcType,
        field: IrSimpleFunction,
        method: IrSimpleFunction
    ) =
        with(pluginContext.createIrBuilder(irField.symbol)) {
            irCallConstructor(
                rpcMethod.constructors.first(),
                listOf(serviceInterface.typeWith(), inputType, outputType)
            ).apply {
                this.type = rpcMethod.typeWith(serviceInterface.typeWith(), inputType, outputType)
                putValueArgument(0, irString(endpoint))
                putValueArgument(
                    1,
                    when (type) {
                        RpcType.BINARY_INPUT -> irGetObject(binaryTransformer)
                        else ->
                            irCallConstructor(
                                serializerTransformer.constructors.first(),
                                listOf(inputType)
                            ).apply {
                                this.type = serializerTransformer.typeWith(inputType)
                                putValueArgument(0, getSerializer(this@with, inputType))
                            }
                    }
                )
                putValueArgument(
                    2,
                    when (type) {
                        RpcType.BINARY_OUTPUT -> irGetObject(binaryTransformer)
                        RpcType.SERVICE ->
                            irCallConstructor(
                                subserviceTransformer.constructors.first(),
                                listOf(outputType)
                            ).apply {
                                this.type = subserviceTransformer.typeWith(outputType)
                                putValueArgument(
                                    0,
                                    irGetObject(
                                        outputType.getClass()?.companionObject()?.symbol
                                            ?: error("Missing companion")
                                    )
                                )
                            }
                        else ->
                            irCallConstructor(
                                serializerTransformer.constructors.first(),
                                listOf(outputType)
                            ).apply {
                                this.type = serializerTransformer.typeWith(outputType)
                                putValueArgument(0, getSerializer(this@with, outputType))
                            }
                    }
                )
                val createServiceExecutor = irCreateServiceExecutor(method, serviceInterface)
                putValueArgument(
                    3,
                    irBlock {
                        +irCallConstructor(
                            createServiceExecutor.constructors.first().symbol,
                            emptyList()
                        ).apply {
                            this.type = createServiceExecutor.typeWith()
                        }
                    }
                )
            }
        }

    private fun KsrpcGenerationEnvironment.irCreateServiceExecutor(
        referencedFunction: IrSimpleFunction,
        receiver: IrClass
    ) =
        pluginContext.irFactory.buildClass {
            startOffset = referencedFunction.startOffset
            endOffset = referencedFunction.endOffset
            name = Name.identifier(
                "Anonymous${referencedFunction.name.asString().capitalizeAsciiOnly()}"
            )
            visibility = DescriptorVisibilities.INTERNAL
            kind = ClassKind.CLASS
            modality = Modality.FINAL
            isCompanion = false
        }.apply {
            this.parent = receiver
            superTypes = listOf(serviceExecutor.typeWith())
            createImplicitParameterDeclarationWithWrappedDescriptor()

            addConstructor {
                startOffset = SYNTHETIC_OFFSET
                endOffset = SYNTHETIC_OFFSET
                isPrimary = true
            }.apply {
                body = pluginContext.createIrBuilder(symbol).irBlockBody {
                    +irDelegatingConstructorCall(
                        context.irBuiltIns.anyClass.owner.constructors.single()
                    )
                }
            }
            val invoke = serviceExecutor.functions.find {
                it.owner.name.asString() == FqConstants.INVOKE
            } ?: error("Can't find invoke method")
            overrideMethod(invoke, returnType = referencedFunction.returnType) { override ->
                +irReturn(
                    irCall(referencedFunction).apply {
                        type = referencedFunction.returnType
                        dispatchReceiver = irImplicitCast(
                            irGet(override.valueParameters[0]),
                            receiver.typeWith()
                        )
                        putValueArgument(
                            0,
                            irImplicitCast(
                                irGet(override.valueParameters[1]),
                                referencedFunction.valueParameters[0].type
                            )
                        )
                    }
                )
            }
            receiver.addChild(this)
        }

    private fun KsrpcGenerationEnvironment.getSerializer(
        irBlockBodyBuilder: DeclarationIrBuilder,
        type: IrType
    ) =
        irBlockBodyBuilder.irCall(
            serializerMethod
                ?: error("Missing serializer")
        ).apply {
            putTypeArgument(0, type)
            this.type = kSerializer.typeWith(type)
        }

    private fun KsrpcGenerationEnvironment.determineType(
        method: IrFunction,
        inputType: IrType,
        outputType: IrType
    ): RpcType {
        if (method.returnType.extends(rpcService)) {
            return RpcType.SERVICE
        }
        if (inputType.classFqName == byteReadChannel) {
            return RpcType.BINARY_INPUT
        }
        if (outputType.classFqName == byteReadChannel) {
            return RpcType.BINARY_OUTPUT
        }
        return RpcType.DEFAULT
    }

    private fun IrType.extends(cls: IrClassSymbol): Boolean {
        if (classFqName == cls.owner.kotlinFqName) {
            return true
        }
        return superTypes().any { t ->
            t.extends(cls)
        }
    }

    private enum class RpcType {
        DEFAULT,
        BINARY_INPUT,
        BINARY_OUTPUT,
        SERVICE
    }
}