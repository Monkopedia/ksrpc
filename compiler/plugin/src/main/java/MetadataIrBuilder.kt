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

import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.builders.irBoolean
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irLong
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irVararg
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.expressions.IrClassReference
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetEnumValue
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.starProjectedType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.SYNTHETIC_OFFSET
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.parentClassOrNull

/**
 * Builds IR expressions that materialize captured sibling-annotation arguments
 * into `List<MethodMetadata>` at runtime, for inclusion in the generated
 * RpcMethod descriptor.
 *
 * Supported annotation argument shapes are: primitive constants (String, Int,
 * Long, Boolean, Double, Float), KClass literals, enum constants, and arrays
 * (including varargs) of any of the above. Nested annotations are not
 * supported — they cause a compile error.
 */
class MetadataIrBuilder(
    private val env: KsrpcGenerationEnvironment,
    private val builder: DeclarationIrBuilder
) {
    init {
        check(env.metadataSupported) {
            "MetadataIrBuilder created without metadata-capable dependencies"
        }
    }

    private val methodMetadata = env.methodMetadata!!
    private val metadataValue = env.metadataValue!!
    private val pair = env.pair!!
    private val toFunction = env.toFunction!!
    private val stringType = env.context.irBuiltIns.stringType
    private val metadataValueType = metadataValue.starProjectedType
    private val pairType = pair.typeWith(stringType, metadataValueType)

    fun buildMetadataList(metadataAnnotations: List<IrConstructorCall>): IrExpression = buildListOf(
        methodMetadata.starProjectedType,
        metadataAnnotations.map { buildMetadata(it) }
    )

    private fun buildMetadata(annotation: IrConstructorCall): IrExpression {
        val annotationFqName = annotation.type.classFqName?.asString()
            ?: error("Sibling annotation has no FQ name")
        val annotationClass = annotation.symbol.owner.parentClassOrNull
            ?: error("Sibling annotation $annotationFqName has no parent class")
        val ctor = annotationClass.constructors.first()
        val pairs = mutableListOf<IrExpression>()
        for ((index, arg) in annotation.arguments.withIndex()) {
            if (index >= ctor.parameters.size) break
            val param = ctor.parameters[index]
            if (param.kind != IrParameterKind.Regular) continue
            val expr = arg ?: continue
            val name = param.name.asString()
            val value = buildMetadataValue(expr)
                ?: error(
                    "Unsupported annotation argument $annotationFqName.$name: " +
                        expr::class.simpleName
                )
            pairs.add(buildPair(name, value))
        }

        return builder.irCallConstructor(
            methodMetadata.constructors.first(),
            emptyList()
        ).apply {
            type = methodMetadata.starProjectedType
            putArgs(
                builder.irString(annotationFqName),
                buildPairsList(pairs)
            )
        }
    }

    private fun buildPairsList(pairs: List<IrExpression>): IrExpression =
        buildListOf(pairType, pairs)

    private fun buildPair(name: String, value: IrExpression): IrExpression =
        builder.irCall(toFunction).apply {
            typeArguments[0] = stringType
            typeArguments[1] = metadataValueType
            val params = toFunction.owner.parameters
            val extReceiver = params.single { it.kind == IrParameterKind.ExtensionReceiver }
            val regular = params.single { it.kind == IrParameterKind.Regular }
            arguments[extReceiver] = builder.irString(name)
            arguments[regular] = value
            type = pair.typeWith(stringType, metadataValueType)
        }

    private fun buildMetadataValue(expr: IrExpression): IrExpression? = when (expr) {
        is IrConst -> when (val v = expr.value) {
            is String -> ctor(env.metadataValueString!!, builder.irString(v))

            is Int -> ctor(env.metadataValueInt!!, builder.irInt(v))

            is Long -> ctor(env.metadataValueLong!!, builder.irLong(v))

            is Boolean -> ctor(env.metadataValueBoolean!!, builder.irBoolean(v))

            is Double -> ctor(
                env.metadataValueDouble!!,
                IrConstImpl.double(
                    SYNTHETIC_OFFSET,
                    SYNTHETIC_OFFSET,
                    env.context.irBuiltIns.doubleType,
                    v
                )
            )

            is Float -> ctor(
                env.metadataValueFloat!!,
                IrConstImpl.float(
                    SYNTHETIC_OFFSET,
                    SYNTHETIC_OFFSET,
                    env.context.irBuiltIns.floatType,
                    v
                )
            )

            else -> null
        }

        is IrClassReference -> ctor(env.metadataValueKClass!!, expr)

        is IrGetEnumValue -> ctor(env.metadataValueEnum!!, expr)

        is IrVararg -> {
            val items = expr.elements.mapNotNull { element ->
                val itemExpr = element as? IrExpression ?: return@mapNotNull null
                buildMetadataValue(itemExpr)
            }
            ctor(env.metadataValueList!!, buildListOf(metadataValueType, items))
        }

        else -> null
    }

    private fun buildListOf(elementType: IrType, items: List<IrExpression>): IrExpression =
        builder.irCall(env.listOfFunction).apply {
            typeArguments[0] = elementType
            val varargParameter = env.listOfFunction.owner.parameters
                .single { it.kind == IrParameterKind.Regular }
            arguments[varargParameter] = builder.irVararg(elementType, items)
        }

    private fun ctor(symbol: IrClassSymbol, vararg args: IrExpression): IrExpression =
        builder.irCallConstructor(symbol.constructors.first(), emptyList()).apply {
            type = symbol.starProjectedType
            putArgs(*args)
        }
}
