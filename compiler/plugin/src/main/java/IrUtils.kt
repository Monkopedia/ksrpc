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
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.builders.IrBlockBodyBuilder
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.declarations.addConstructor
import org.jetbrains.kotlin.ir.builders.declarations.addField
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.builders.declarations.buildField
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irAnnotation
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irConcat
import org.jetbrains.kotlin.ir.builders.irDelegatingConstructorCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irImplicitCast
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irVararg
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrSymbolOwner
import org.jetbrains.kotlin.ir.expressions.IrClassReference
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrMemberAccessExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrClassReferenceImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrStringConcatenationImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.starProjectedType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.SYNTHETIC_OFFSET
import org.jetbrains.kotlin.ir.util.addChild
import org.jetbrains.kotlin.ir.util.allOverridden
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.createThisReceiverParameter
import org.jetbrains.kotlin.ir.util.defaultType as irClassDefaultType
import org.jetbrains.kotlin.ir.util.erasedUpperBound
import org.jetbrains.kotlin.ir.util.fqNameForIrSerialization
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.getAllSuperclasses
import org.jetbrains.kotlin.ir.util.parentClassOrNull
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.util.capitalizeDecapitalize.capitalizeAsciiOnly

fun IrBuilderWithScope.buildStringFormat(vararg args: IrExpression): IrStringConcatenationImpl =
    irConcat().apply {
        arguments += args
    }

/**
 * Emit an `IrGetEnumValue` expression for the given [entryName] on the `ServiceTier` enum.
 */
internal fun IrBuilderWithScope.irGetEnumValue(
    env: KsrpcGenerationEnvironment,
    entryName: String
): IrExpression {
    val enumClass = env.serviceTierClass.owner
    val entry = enumClass.declarations
        .filterIsInstance<org.jetbrains.kotlin.ir.declarations.IrEnumEntry>()
        .first { it.name.asString() == entryName }
    return org.jetbrains.kotlin.ir.expressions.impl.IrGetEnumValueImpl(
        SYNTHETIC_OFFSET,
        SYNTHETIC_OFFSET,
        env.serviceTierClass.defaultType,
        entry.symbol
    )
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
    val classType = irClass.irClassDefaultType
    val classSymbol = irClass.symbol
    return IrClassReferenceImpl(
        startOffset,
        endOffset,
        context.irBuiltIns.kClassClass.starProjectedType,
        classSymbol,
        classType
    )
}

/**
 * Add `@KsrpcGenerated` to [irClass] when the annotation is on the compile
 * classpath. No-op when the marker is missing (older ksrpc-api) or when the
 * class already carries the annotation. See issue #168 — every synthetic
 * declaration the plugin emits (Stub, Obj, Companion, subtype companion)
 * should be tagged so BCV's `nonPublicMarkers` filter can hide them.
 */
internal fun IrClass.addKsrpcGeneratedAnnotation(
    context: IrPluginContext,
    env: KsrpcGenerationEnvironment
) {
    val markerSymbol = env.ksrpcGenerated ?: return
    val markerFqName = markerSymbol.owner.fqNameWhenAvailable
    if (annotations.any { it.type.classFqName == markerFqName }) return
    val constructor = markerSymbol.constructors.firstOrNull()
        ?: reportInternal(
            "@KsrpcGenerated has no constructor — ksrpc-api on the classpath is broken"
        )
    annotations += context.irBuilder(symbol).irAnnotation(constructor, emptyList())
}

fun IrClassSymbol.findMethod(name: Name) = functions.find {
    it.owner.name == name
} ?: reportInternal(
    "can't find ${name.asString()} on ${owner.fqNameWhenAvailable?.asString() ?: owner.name.asString()}"
)

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

/**
 * Determine the service tier from an `@KsService` interface's supertypes.
 * Returns "SIMPLE", "HOST", or "BIDI".
 */
internal fun computeServiceTier(irClass: IrClass): String = when {
    irClass.getAllSuperclasses().any {
        it.fqNameForIrSerialization == FqConstants.FQRPC_BIDI_SERVICE
    } -> "BIDI"
    irClass.getAllSuperclasses().any {
        it.fqNameForIrSerialization == FqConstants.FQRPC_HOST_SERVICE
    } -> "HOST"
    else -> "SIMPLE"
}

fun IrSimpleFunction.overridesFunctionIn(fqName: FqName): Boolean =
    parentClassOrNull?.fqNameWhenAvailable == fqName ||
        allOverridden().any { it.parentClassOrNull?.fqNameWhenAvailable == fqName }

fun IrMemberAccessExpression<*>.putArgs(vararg args: IrExpression) {
    args.forEachIndexed { index, irExpression ->
        arguments[index] = irExpression
    }
}

/**
 * Single source of truth for the `"${Tn}Serializer"` field/parameter naming scheme used
 * across generated Obj / Stub declarations. Must match the name chosen in the FIR
 * generators (see [serializerParameterName]).
 */
internal fun serializerFieldName(typeParameterName: Name): Name =
    Name.identifier(typeParameterName.asString() + "Serializer")

/**
 * Emits one private `KSerializer<Tn>` `IrField` per type parameter on [container],
 * attaching the fields to [container] via [addField] when [attach] is true. When
 * [attach] is false the fields are built free-standing (StubGeneration uses this
 * because [generateChildrenForClass] attaches them itself via its returned list).
 */
internal fun IrPluginContext.buildSerializerFieldsForTypeParams(
    container: IrClass,
    env: KsrpcGenerationEnvironment,
    attach: Boolean
): List<IrField> = container.typeParameters.map { tp ->
    val fieldType = env.kSerializer.typeWith(tp.defaultType)
    val fieldName = serializerFieldName(tp.name)
    if (attach) {
        container.addField {
            startOffset = SYNTHETIC_OFFSET
            endOffset = SYNTHETIC_OFFSET
            name = fieldName
            origin = IrDeclarationOrigin.DELEGATE
            visibility = DescriptorVisibilities.PRIVATE
            type = fieldType
            isFinal = true
            isStatic = false
        }
    } else {
        irFactory.buildField {
            startOffset = SYNTHETIC_OFFSET
            endOffset = SYNTHETIC_OFFSET
            name = fieldName
            origin = IrDeclarationOrigin.DELEGATE
            visibility = DescriptorVisibilities.PRIVATE
            type = fieldType
            isFinal = true
            isStatic = false
        }
    }
}

/**
 * Build the anonymous `ServiceExecutor` implementation class for [referencedFunction],
 * parented to [container]. The class name follows the historical
 * `"Anonymous${CapitalizedFunctionName}"` convention so both the generic and
 * non-generic code paths produce identical output. The override of
 * `ServiceExecutor.invoke` defers to [referencedFunction] with implicit casts on the
 * dispatch receiver and value argument.
 *
 * Shared between [StubGeneration] (non-generic services) and [GenericMethodIrBuilder]
 * (generic services).
 */
internal fun IrPluginContext.buildAnonymousServiceExecutor(
    env: KsrpcGenerationEnvironment,
    referencedFunction: IrSimpleFunction,
    container: IrClass
): IrClass {
    val executorClass = irFactory.buildClass {
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
        parent = container
        superTypes = listOf(env.serviceExecutor.typeWith())
        // Mark the synthetic anonymous ServiceExecutor `@KsrpcGenerated` so BCV
        // consumers can filter generated synthetic declarations out of API dumps
        // (issue #168). Despite being declared INTERNAL these still appear in
        // jvm BCV output.
        addKsrpcGeneratedAnnotation(this@buildAnonymousServiceExecutor, env)
        createThisReceiverParameter()
        addConstructor {
            startOffset = SYNTHETIC_OFFSET
            endOffset = SYNTHETIC_OFFSET
            isPrimary = true
        }.apply {
            body = irBuilder(symbol).irBlockBody {
                +irDelegatingConstructorCall(
                    irBuiltIns.anyClass.owner.constructors.single()
                )
            }
        }
    }
    val invokeMethod = env.serviceExecutor.findMethod(FqConstants.INVOKE)
    // The referenced user function may be declared with 0 or 1 value parameters.
    // ServiceExecutor.invoke always passes (service, input: Any?); for 0-arg functions
    // we simply discard the input and call with just the dispatch receiver.
    val referencedValueParams = referencedFunction.parameters.filter {
        it != referencedFunction.dispatchReceiverParameter
    }
    val override = overrideMethod(
        executorClass,
        invokeMethod,
        referencedFunction.returnType
    ) { override ->
        +irReturn(
            irCall(referencedFunction).apply {
                type = referencedFunction.returnType
                val dispatchArg = irImplicitCast(
                    irGet(override.parameters[1]),
                    referencedFunction.dispatchReceiverParameter?.type!!
                )
                if (referencedValueParams.isEmpty()) {
                    // 0-arg @KsMethod: ignore the `input: Any?` parameter entirely.
                    putArgs(dispatchArg)
                } else {
                    putArgs(
                        dispatchArg,
                        irImplicitCast(
                            irGet(override.parameters[2]),
                            referencedValueParams[0].type
                        )
                    )
                }
            }
        )
    }
    executorClass.addChild(override)
    container.addChild(executorClass)
    return executorClass
}

/**
 * Emit a `listOf<String>(v0, v1, ...)` IR expression via the vararg overload of
 * `kotlin.collections.listOf`. Shared between CompanionGeneration and ObjGeneration which
 * both emit `endpoints` property bodies.
 */
internal fun IrBuilderWithScope.irListOfStrings(
    env: KsrpcGenerationEnvironment,
    values: List<String>
): IrExpression = irCall(env.listOfFunction).apply {
    typeArguments[0] = context.irBuiltIns.stringType
    val varargParameter = env.listOfFunction.owner.parameters
        .single { it.kind == IrParameterKind.Regular }
    arguments[varargParameter] = irVararg(
        context.irBuiltIns.stringType,
        values.map { irString(it) }
    )
}

// ---------------------------------------------------------------------------
// IR builder DSL helpers (issue #66)
// ---------------------------------------------------------------------------

/**
 * Construct an instance of [classSymbol] with no type arguments, setting [IrConstructorCall.type]
 * to the class's star-projected type and passing [args] as positional value arguments.
 *
 * Covers the very common pattern:
 * ```
 * irCallConstructor(cls.constructors.first(), emptyList()).apply {
 *     type = cls.starProjectedType
 *     putArgs(a, b, c)
 * }
 * ```
 */
internal fun IrBuilderWithScope.irConstructOf(
    classSymbol: IrClassSymbol,
    vararg args: IrExpression
): IrConstructorCall = irCallConstructor(
    classSymbol.constructors.first(),
    emptyList()
).apply {
    type = classSymbol.starProjectedType
    putArgs(*args)
}

/**
 * Construct an instance of [classSymbol] with the given [typeArguments], setting
 * [IrConstructorCall.type] to `classSymbol.typeWith(typeArguments)` and passing [args]
 * as positional value arguments.
 *
 * Covers the pattern:
 * ```
 * irCallConstructor(cls.constructors.first(), typeArgs).apply {
 *     type = cls.typeWith(typeArgs)
 *     putArgs(a, b, c)
 * }
 * ```
 */
internal fun IrBuilderWithScope.irConstructOf(
    classSymbol: IrClassSymbol,
    typeArguments: List<IrType>,
    vararg args: IrExpression
): IrConstructorCall = irCallConstructor(
    classSymbol.constructors.first(),
    typeArguments
).apply {
    type = classSymbol.typeWith(typeArguments)
    putArgs(*args)
}

/**
 * Emit a `listOf<E>(items...)` IR expression via the vararg overload of
 * `kotlin.collections.listOf`. Shared by [ErrorMappingIrBuilder],
 * [ContextBindingIrBuilder], and [MetadataIrBuilder] which all need to
 * materialize `List<X>` at runtime.
 */
internal fun IrBuilderWithScope.irBuildListOf(
    env: KsrpcGenerationEnvironment,
    elementType: IrType,
    items: List<IrExpression>
): IrExpression = irCall(env.listOfFunction).apply {
    typeArguments[0] = elementType
    val varargParameter = env.listOfFunction.owner.parameters
        .single { it.kind == IrParameterKind.Regular }
    arguments[varargParameter] = irVararg(elementType, items)
}
