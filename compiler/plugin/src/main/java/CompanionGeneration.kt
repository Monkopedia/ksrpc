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

import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.common.lower.irThrow
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.builders.IrBlockBodyBuilder
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irBranch
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irConcat
import org.jetbrains.kotlin.ir.builders.irEquals
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.SYNTHETIC_OFFSET
import org.jetbrains.kotlin.ir.util.companionObject
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.createImplicitParameterDeclarationWithWrappedDescriptor
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.name.Name

object CompanionGeneration {
    fun generate(
        cls: ServiceClass,
        env: KsrpcGenerationEnvironment,
        clsType: IrSimpleType,
        stubCls: IrClass,
        methods: Map<String, IrFunction>
    ) = with(env) {
        val companion = cls.irClass.companionObject() ?: pluginContext.irFactory.buildClass {
            this.name = Name.identifier("Companion")
            this.modality = Modality.FINAL
            this.visibility = DescriptorVisibilities.PUBLIC
            this.isCompanion = true
            this.kind = ClassKind.OBJECT
            startOffset = SYNTHETIC_OFFSET
            endOffset = SYNTHETIC_OFFSET
        }.apply {
            origin = IrDeclarationOrigin.IR_EXTERNAL_DECLARATION_STUB
            superTypes = listOf(env.rpcObject.typeWith(clsType))
            createImplicitParameterDeclarationWithWrappedDescriptor()
        }
        rpcObjectKey?.let {
            cls.irClass.annotations += pluginContext.createIrBuilder(cls.irClass.symbol)
                .irCallConstructor(
                    it.constructors.find { it.owner.isPrimary } ?: error("No primary constructor"),
                    emptyList()
                ).apply {
                    putValueArgument(
                        0,
                        createClassReference(companion, SYNTHETIC_OFFSET, SYNTHETIC_OFFSET)
                    )
                }
        }
        companion.build {
            val existing = companion.functions.find {
                it.name.asString() == FqConstants.CREATE_STUB && it.modality == Modality.FINAL
            } ?: error("Missing implementation")

            existing.body = pluginContext.createIrBuilder(existing.symbol).irSynthBody {
                +irReturn(
                    irCallConstructor(
                        stubCls.primaryConstructor?.symbol
                            ?: error("Invalid constructor declaration"),
                        emptyList()
                    ).apply {
                        for (param in existing.valueParameters) {
                            putValueArgument(param.index, irGet(param))
                        }
                    }
                )
            }
            val existingFindEndpoint = companion.functions.find {
                it.name.asString() == FqConstants.FIND_ENDPOINT && it.modality == Modality.FINAL
            } ?: error("Missing implementation")
            val builder = pluginContext.createIrBuilder(existingFindEndpoint.symbol)
            existingFindEndpoint.body = builder.irBlockBody {
                val input = existingFindEndpoint.valueParameters[0]
                +irReturn(
                    irWhen(existingFindEndpoint.returnType) {
                        for ((endpoint, method) in methods) {
                            +irBranch(
                                irEquals(irString(endpoint.trimStart('/')), irGet(input)),
                                irCall(method).apply {
                                    dispatchReceiver = irGetObject(
                                        stubCls.companionObject()!!.symbol
                                    )
                                }
                            )
                        }
                        +elseBranch(
                            irThrow(
                                irCallConstructor(illegalArgumentStrConstructor, emptyList())
                                    .apply {
                                        putValueArgument(
                                            0,
                                            irConcat().apply {
                                                arguments += irString("Unknown endpoint: ")
                                                arguments += irGet(input)
                                                arguments += irString(
                                                    " in service " +
                                                        cls.irClass.kotlinFqName.asString()
                                                )
                                            }
                                        )
                                    }
                            )
                        )
                    }
                )
            }
        }
    }
}

inline fun DeclarationIrBuilder.irSynthBody(builder: IrBlockBodyBuilder.() -> Unit) = irBlockBody {
    startOffset = SYNTHETIC_OFFSET
    endOffset = SYNTHETIC_OFFSET
    builder()
}
