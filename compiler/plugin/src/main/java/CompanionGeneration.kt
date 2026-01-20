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

import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.irThrow
import org.jetbrains.kotlin.ir.backend.js.utils.isDispatchReceiver
import org.jetbrains.kotlin.ir.builders.IrBlockBodyBuilder
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irBranch
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irElseBranch
import org.jetbrains.kotlin.ir.builders.irEquals
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irWhen
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.expressions.IrClassReference
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.kotlinFqName

class CompanionGeneration(
    context: IrPluginContext,
    private val classes: MutableMap<String, ServiceClass>,
    private val env: KsrpcGenerationEnvironment
) : AbstractTransformerForGenerator(context) {

    override fun interestedIn(key: GeneratedDeclarationKey?): Boolean =
        key is FirCompanionDeclarationGenerator.Key

    override fun generateChildrenForClass(
        declaration: IrClass,
        key: GeneratedDeclarationKey?
    ): Collection<IrDeclaration> {
        val k = key as FirCompanionDeclarationGenerator.Key
        val cls = classes[k.type]
            ?: error("Invalid synthetic declaration for ${k.type} in ${classes.keys}")
        env.rpcObjectKey?.let {
            val objectReference = createClassReference(context, declaration)
            cls.irClassAndImpls.forEach { irClass ->
                irClass.annotations += createRpcObjectAnnotation(irClass, it, objectReference)
            }
        }
        declaration.isCompanion = true
        return emptyList()
    }

    private fun createRpcObjectAnnotation(
        irClass: IrClass,
        rpcObjectKey: IrClassSymbol,
        objectReference: IrClassReference
    ): IrConstructorCall {
        val primaryConstructor = rpcObjectKey.constructors.find { it.owner.isPrimary }
            ?: error("No primary constructor")
        return context.irBuilder(irClass).irCallConstructor(primaryConstructor, emptyList())
            .apply { putArgs(objectReference) }
    }

    override fun generateBodyForFunction(
        function: IrSimpleFunction,
        key: GeneratedDeclarationKey?
    ): IrBody? {
        val k = key as FirCompanionDeclarationGenerator.Key
        val cls = classes[k.type]
            ?: error("Invalid synthetic declaration for ${k.type} in ${classes.keys}")
        return when (function.name) {
            FqConstants.CREATE_STUB -> buildCreateStubBody(function, cls)
            FqConstants.FIND_ENDPOINT -> buildFindEndpointBody(function, cls)
            else -> null
        }
    }

    private fun buildFindEndpointBody(function: IrSimpleFunction, cls: ServiceClass): IrBlockBody =
        context.irBuilder(function).irBlockBody {
            val input = function.parameters.first { !it.isDispatchReceiver }
            val branches = buildList {
                for ((endpoint, method) in cls.endpoints) {
                    this += irBranch(
                        irEquals(irString(endpoint.trimStart('/')), irGet(input)),
                        irCall(method).apply {
                            putArgs(irGetObject(cls.stubCompanion.symbol))
                        }
                    )
                }
                val message = buildStringFormat(
                    irString("Unknown endpoint: "),
                    irGet(input),
                    irString(" in service " + cls.irClass.kotlinFqName.asString())
                )
                this += irElseBranch(irThrowIllegalArgument(message))
            }
            +irReturn(irWhen(function.returnType, branches))
        }

    private fun IrBlockBodyBuilder.irThrowIllegalArgument(message: IrExpression) = irThrow(
        irCallConstructor(env.illegalArgumentStrConstructor, emptyList()).apply {
            putArgs(message)
        }
    )

    private fun buildCreateStubBody(function: IrSimpleFunction, cls: ServiceClass) =
        context.irBuilder(function).irSynthBody {
            +irReturn(
                irCallConstructor(cls.stubConstructor.symbol, emptyList()).apply {
                    putArgs(
                        *function.parameters.filter { !it.isDispatchReceiver }.map { irGet(it) }
                            .toTypedArray()
                    )
                }
            )
        }

    override fun generateBodyForConstructor(
        constructor: IrConstructor,
        key: GeneratedDeclarationKey?
    ): IrBody = context.irBuilder(constructor).irSynthBody { }
}
