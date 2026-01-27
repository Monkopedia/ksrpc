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
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.companionObject

class IntrospectionGeneration(
    context: IrPluginContext,
    private val classes: MutableMap<String, ServiceClass>,
    private val env: KsrpcGenerationEnvironment
) : AbstractTransformerForGenerator(context) {

    override fun interestedIn(key: GeneratedDeclarationKey?): Boolean =
        key is FirKsrpcIntrospectionGenerator.Key

    override fun generateBodyForFunction(
        function: IrSimpleFunction,
        key: GeneratedDeclarationKey?
    ): IrBody? {
        val k = key as FirKsrpcIntrospectionGenerator.Key
        val cls = classes[k.type]
            ?: error("Invalid synthetic declaration for ${k.type} in ${classes.keys}")
        return when (function.name) {
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

    override fun generateChildrenForClass(
        declaration: IrClass,
        key: GeneratedDeclarationKey?
    ): Collection<IrDeclaration> = emptyList()

    override fun generateBodyForConstructor(
        constructor: IrConstructor,
        key: GeneratedDeclarationKey?
    ): IrBody = context.irBuilder(constructor).irSynthBody { }
}
