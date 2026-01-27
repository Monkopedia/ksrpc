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
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
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
import org.jetbrains.kotlin.ir.builders.irVararg
import org.jetbrains.kotlin.ir.builders.irWhen
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.expressions.IrClassReference
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.SYNTHETIC_OFFSET
import org.jetbrains.kotlin.ir.util.companionObject
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
        declaration.declarations
            .filterIsInstance<IrProperty>()
            .firstOrNull { it.name == FqConstants.SERVICE_NAME }
            ?.let { property ->
                val fqName = cls.irClass.kotlinFqName.asString()
                property.backingField?.initializer =
                    irFactory.createExpressionBody(
                        SYNTHETIC_OFFSET,
                        SYNTHETIC_OFFSET,
                        context.irBuilder(property).irString(fqName)
                    )
                property.getter?.body = context.irBuilder(property.getter!!).irSynthBody {
                    +irReturn(irString(fqName))
                }
            }
        declaration.declarations
            .filterIsInstance<IrProperty>()
            .firstOrNull { it.name == FqConstants.ENDPOINTS }
            ?.let { property ->
                val endpoints = cls.endpoints.keys.map { it.trimStart('/') }
                val listExpr = context.irBuilder(property).irListOfStrings(endpoints)
                property.backingField?.initializer =
                    irFactory.createExpressionBody(
                        SYNTHETIC_OFFSET,
                        SYNTHETIC_OFFSET,
                        listExpr
                    )
                property.getter?.body = context.irBuilder(property.getter!!).irSynthBody {
                    +irReturn(irListOfStrings(endpoints))
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
        function.correspondingPropertySymbol?.owner?.name?.let { propertyName ->
            if (propertyName == FqConstants.SERVICE_NAME) {
                return context.irBuilder(function).irSynthBody {
                    +irReturn(irString(cls.irClass.kotlinFqName.asString()))
                }
            }
            if (propertyName == FqConstants.ENDPOINTS) {
                val endpoints = cls.endpoints.keys.map { it.trimStart('/') }
                return context.irBuilder(function).irSynthBody {
                    +irReturn(irListOfStrings(endpoints))
                }
            }
        }
        return when (function.name) {
            FqConstants.CREATE_STUB -> buildCreateStubBody(function, cls)
            FqConstants.FIND_ENDPOINT -> buildFindEndpointBody(function, cls)
            FqConstants.GET_INTROSPECTION -> buildIntrospectionBody(function, cls)
            else -> null
        }
    }

    private fun buildIntrospectionBody(function: IrSimpleFunction, cls: ServiceClass): IrBlockBody =
        context.irBuilder(function).irBlockBody {
            +irReturn(
                irCallConstructor(env.introspectionConstructor, emptyList()).apply {
                    putArgs(
                        irGetObject(
                            cls.irClass.companionObject()?.symbol ?: error("Companion is missing")
                        )
                    )
                }
            )
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
                this += irElseBranch(irThrowEndpointNotFound(message))
            }
            +irReturn(irWhen(function.returnType, branches))
        }

    private fun IrBlockBodyBuilder.irThrowEndpointNotFound(message: IrExpression) = irThrow(
        irCallConstructor(env.endpointNotFoundStrConstructor, emptyList()).apply {
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

    private fun IrBuilderWithScope.irListOfStrings(values: List<String>) =
        irCall(env.listOfFunction)
            .apply {
                typeArguments[0] = context.irBuiltIns.stringType
                val varargParameter = env.listOfFunction.owner.parameters
                    .single { it.kind == IrParameterKind.Regular }
                arguments[varargParameter] =
                    irVararg(context.irBuiltIns.stringType, values.map { irString(it) })
            }

    override fun generateBodyForConstructor(
        constructor: IrConstructor,
        key: GeneratedDeclarationKey?
    ): IrBody = context.irBuilder(constructor).irSynthBody { }
}
