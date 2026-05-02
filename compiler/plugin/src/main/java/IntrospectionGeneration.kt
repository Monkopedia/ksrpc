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
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.companionObject
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.kotlinFqName

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
        // Class may have been removed by validation (issue #45). Errors are already
        // reported; skip body generation to avoid crashing.
        val cls = classes[k.type] ?: return null
        return when (function.name) {
            FqConstants.GET_INTROSPECTION -> buildIntrospectionBody(function, cls)
            else -> null
        }
    }

    private fun buildIntrospectionBody(function: IrSimpleFunction, cls: ServiceClass): IrBlockBody =
        context.irBuilder(function).irBlockBody {
            val rpcObjectExpr: IrExpression = if (cls.isGeneric) {
                // For generic services the companion is an RpcObjectFactory, not an
                // RpcObject. We construct the nested Obj class with dummy
                // serializer<Any?>() args — introspection never performs actual
                // (de)serialization, so the concrete serializers are irrelevant.
                val objClass = cls.irClass.declarations
                    .filterIsInstance<IrClass>()
                    .firstOrNull { it.name == FqConstants.OBJ }
                    ?: reportInternal(
                        "missing generated Obj class on " +
                            cls.irClass.kotlinFqName.asString() +
                            " — FirKsrpcObjGenerator did not run"
                    )
                val objConstructor = objClass.constructors.first { it.isPrimary }
                val arity = cls.irClass.typeParameters.size
                // Use Unit as the dummy type argument — it is always
                // @Serializable, unlike Any/Any? which have no serializer.
                val unitType = context.irBuiltIns.unitType
                val objTypeArgs = List(arity) { unitType }
                val serializerFn = env.serializerMethod ?: reportInternal(
                    "can't resolve kotlinx.serialization.serializer<T>() — " +
                        "kotlinx-serialization-core must be on the compile classpath"
                )
                val dummySerializers = List(arity) {
                    irCall(serializerFn).apply {
                        typeArguments[0] = unitType
                        type = env.kSerializer.typeWith(unitType)
                    }
                }
                irCallConstructor(objConstructor.symbol, objTypeArgs).apply {
                    type = objClass.typeWith(objTypeArgs)
                    putArgs(*dummySerializers.toTypedArray())
                }
            } else {
                // Non-generic: companion IS the RpcObject.
                irGetObject(
                    cls.irClass.companionObject()?.symbol ?: reportInternal(
                        "companion is missing on " +
                            cls.irClass.kotlinFqName.asString() +
                            " — CompanionGeneration should have produced it"
                    )
                )
            }
            +irReturn(irConstructOf(env.introspectionImpl, rpcObjectExpr))
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
