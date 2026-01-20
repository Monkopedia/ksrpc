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
package com.monkopedia.ksrpc.plugin

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.builders.IrBlockBodyBuilder
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irConcat
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrSymbolOwner
import org.jetbrains.kotlin.ir.expressions.IrClassReference
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrMemberAccessExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrClassReferenceImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrStringConcatenationImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.starProjectedType
import org.jetbrains.kotlin.ir.util.SYNTHETIC_OFFSET
import org.jetbrains.kotlin.ir.util.allOverridden
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.erasedUpperBound
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.parentClassOrNull
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

fun IrBuilderWithScope.buildStringFormat(vararg args: IrExpression): IrStringConcatenationImpl =
    irConcat().apply {
        arguments += args
    }

inline fun DeclarationIrBuilder.irSynthBody(builder: IrBlockBodyBuilder.() -> Unit) = irBlockBody {
    startOffset = SYNTHETIC_OFFSET
    endOffset = SYNTHETIC_OFFSET
    builder()
}

fun createClassReference(
    context: IrPluginContext,
    irClass: IrClass,
    startOffset: Int = SYNTHETIC_OFFSET,
    endOffset: Int = SYNTHETIC_OFFSET
): IrClassReference {
    val classType = irClass.defaultType
    val classSymbol = irClass.symbol
    return IrClassReferenceImpl(
        startOffset,
        endOffset,
        context.irBuiltIns.kClassClass.starProjectedType,
        classSymbol,
        classType
    )
}

fun IrClassSymbol.findMethod(name: Name) = functions.find {
    it.owner.name == name
} ?: error("Can't find ${name.asString()} method")

fun IrPluginContext.irBuilder(owner: IrSymbolOwner) = irBuilder(owner.symbol)

@OptIn(UnsafeDuringIrConstructionAPI::class)
fun IrPluginContext.irBuilder(symbol: IrSymbol) = DeclarationIrBuilder(
    this,
    symbol,
    symbol.owner.startOffset.takeIf { it != 0 }
        ?: SYNTHETIC_OFFSET,
    symbol.owner.endOffset.takeIf { it != 0 }
        ?: SYNTHETIC_OFFSET
)

fun IrClass.isSubclassOfFqName(fqName: String): Boolean =
    fqNameWhenAvailable?.asString() == fqName ||
        superTypes.any {
            it.erasedUpperBound.isSubclassOfFqName(fqName)
        }

fun IrSimpleFunction.overridesFunctionIn(fqName: FqName): Boolean =
    parentClassOrNull?.fqNameWhenAvailable == fqName ||
        allOverridden().any { it.parentClassOrNull?.fqNameWhenAvailable == fqName }

fun IrMemberAccessExpression<*>.putArgs(vararg args: IrExpression) {
    args.forEachIndexed { index, irExpression ->
        arguments[index] = irExpression
    }
}
