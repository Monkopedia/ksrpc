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
import org.jetbrains.kotlin.ir.builders.irIfThen
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irNotEquals
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irWhen
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrEnumEntry
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.expressions.IrClassReference
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrGetEnumValueImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.SYNTHETIC_OFFSET
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.name.Name

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
        // The class may have been removed from `classes` by validation (e.g. a @KsService
        // subtype of another @KsService — see issue #45). In that case validation has
        // already reported an error; just skip body generation so we don't crash.
        val cls = classes[k.type] ?: return emptyList()
        // Mark the generated companion `@KsrpcGenerated` so BCV consumers can filter
        // synthetic declarations out of API dumps (issue #168). Applies to both
        // non-generic service companions (RpcObject) and generic service companions
        // (RpcObjectFactory) — both are entirely plugin-generated.
        declaration.addKsrpcGeneratedAnnotation(context, env)
        // Emit @RpcObjectKey pointing at the companion. For non-generic services the
        // companion is the `RpcObject`; for generic services it's the `RpcObjectFactory`.
        // `rpcObject<T>()` inspects the returned instance and, when it's a factory,
        // resolves typeArgs from `typeOf<T>()` and calls `create(...)`.
        env.rpcObjectKey?.let { rpcObjectKeySymbol ->
            val rpcObjectKeyFqName = rpcObjectKeySymbol.owner.kotlinFqName
            val objectReference = createClassReference(context, declaration)
            cls.irClassAndImpls.forEach { irClass ->
                // Skip classes that already have @RpcObjectKey (e.g. plain-Kotlin subtypes
                // whose synthesized companion already added the annotation via
                // SubtypeCompanionGeneration — see issue #160).
                val alreadyAnnotated = irClass.annotations.any {
                    it.type.classFqName == rpcObjectKeyFqName
                }
                if (!alreadyAnnotated) {
                    irClass.annotations += createRpcObjectAnnotation(irClass, rpcObjectKeySymbol, objectReference)
                }
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
                val listExpr = context.irBuilder(property).irListOfStrings(env, endpoints)
                property.backingField?.initializer =
                    irFactory.createExpressionBody(
                        SYNTHETIC_OFFSET,
                        SYNTHETIC_OFFSET,
                        listExpr
                    )
                property.getter?.body = context.irBuilder(property.getter!!).irSynthBody {
                    +irReturn(irListOfStrings(env, endpoints))
                }
            }
        // Emit serviceTier property body.
        val tier = computeServiceTier(cls.irClass)
        declaration.declarations
            .filterIsInstance<IrProperty>()
            .firstOrNull { it.name == FqConstants.SERVICE_TIER }
            ?.let { property ->
                property.getter?.body = context.irBuilder(property.getter!!).irSynthBody {
                    +irReturn(irGetEnumValue(env, tier))
                }
            }
        // For generic service companions, eagerly emit the `arity` getter body — the
        // property overrides RpcObjectFactory.arity and must return a fixed integer.
        if (k.isGeneric) {
            declaration.declarations
                .filterIsInstance<IrProperty>()
                .firstOrNull { it.name == FqConstants.ARITY }
                ?.let { property ->
                    val arity = cls.irClass.typeParameters.size
                    property.getter?.body = context.irBuilder(property.getter!!).irSynthBody {
                        +irReturn(irInt(arity))
                    }
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
            ?: reportInternal(
                "RpcObjectKey has no primary constructor — ksrpc-core runtime must " +
                    "match the compiler plugin"
            )
        return context.irBuilder(irClass).irCallConstructor(primaryConstructor, emptyList())
            .apply { putArgs(objectReference) }
    }

    override fun generateBodyForFunction(
        function: IrSimpleFunction,
        key: GeneratedDeclarationKey?
    ): IrBody? {
        val k = key as FirCompanionDeclarationGenerator.Key
        // See generateChildrenForClass: validation may have removed the entry.
        val cls = classes[k.type] ?: return null
        function.correspondingPropertySymbol?.owner?.name?.let { propertyName ->
            if (propertyName == FqConstants.SERVICE_NAME) {
                return context.irBuilder(function).irSynthBody {
                    +irReturn(irString(cls.irClass.kotlinFqName.asString()))
                }
            }
            if (propertyName == FqConstants.ENDPOINTS) {
                val endpoints = cls.endpoints.keys.map { it.trimStart('/') }
                return context.irBuilder(function).irSynthBody {
                    +irReturn(irListOfStrings(env, endpoints))
                }
            }
            if (propertyName == FqConstants.ARITY) {
                val arity = cls.irClass.typeParameters.size
                return context.irBuilder(function).irSynthBody {
                    +irReturn(irInt(arity))
                }
            }
            if (propertyName == FqConstants.SERVICE_TIER) {
                val tier = computeServiceTier(cls.irClass)
                return context.irBuilder(function).irSynthBody {
                    +irReturn(irGetEnumValue(env, tier))
                }
            }
        }
        return when (function.name) {
            FqConstants.CREATE_STUB -> buildCreateStubBody(function, cls)
            FqConstants.FIND_ENDPOINT -> buildFindEndpointBody(function, cls)
            FqConstants.INVOKE -> buildInvokeFactoryBody(function, cls)
            FqConstants.CREATE -> buildCreateBody(function, cls)
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
                this += irElseBranch(irThrowEndpointNotFound(message))
            }
            +irReturn(irWhen(function.returnType, branches))
        }

    private fun IrBlockBodyBuilder.irThrowEndpointNotFound(message: IrExpression) = irThrow(
        irCallConstructor(env.endpointStrConstructor, emptyList()).apply {
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

    /**
     * Body for the generic companion's
     * `override fun create(typeArgs: List<KType>): RpcObject<Service<*, *, ...>>`.
     *
     * Validates arity, resolves each `KType` in `typeArgs` to a `KSerializer<Any?>` via
     * `resolveSerializerOrThrow`, then constructs `Obj<Any?, Any?, ...>` with those
     * serializers. The result is typed as `RpcObject<Service<*, ...>>`.
     */
    private fun buildCreateBody(function: IrSimpleFunction, cls: ServiceClass): IrBody {
        val objClass = cls.irClass.declarations.filterIsInstance<IrClass>()
            .firstOrNull { it.name == FqConstants.OBJ }
            ?: reportInternal(
                "missing generated Obj class on ${cls.irClass.kotlinFqName.asString()} " +
                    "— FirKsrpcObjGenerator did not run or generated under a different name"
            )
        val objConstructor = objClass.constructors.first { it.isPrimary }
        val resolveFn = env.resolveSerializerOrThrow
            ?: reportInternal(
                "missing com.monkopedia.ksrpc.resolveSerializerOrThrow — compile against " +
                    "a ksrpc-core that includes RpcObjectFactory"
            )
        val arity = cls.irClass.typeParameters.size
        val serviceName = cls.irClass.kotlinFqName.asString()
        val anyNType = context.irBuiltIns.anyNType
        val objTypeArgs = List(arity) { anyNType }
        // List.get(Int): E
        val listClass = context.irBuiltIns.listClass
        val listGet = listClass.owner.declarations
            .filterIsInstance<IrSimpleFunction>()
            .first { it.name.asString() == "get" && it.parameters.size == 2 }
        // List.size: Int (property)
        val listSize = listClass.owner.declarations
            .filterIsInstance<IrProperty>()
            .first { it.name.asString() == "size" }
        val sizeGetter = listSize.getter!!
        // Use IllegalArgumentException(String?) — resolve via referenceClass. Match the
        // single-value-parameter constructor whose only parameter is kotlin.String? (the
        // CharSequence-based overload is also `kotlin.CharSequence`, which we don't want).
        val iaeClass = context.referenceClass(
            org.jetbrains.kotlin.name.ClassId(
                org.jetbrains.kotlin.name.FqName("kotlin"),
                Name.identifier("IllegalArgumentException")
            )
        ) ?: reportInternal("kotlin.IllegalArgumentException not found on classpath")
        val iaeStringCtor = iaeClass.owner.declarations
            .filterIsInstance<IrConstructor>()
            .firstOrNull { ctor ->
                val params = ctor.parameters.filter { !it.isDispatchReceiver }
                params.size == 1 &&
                    params.single().type.classFqName?.asString() == "kotlin.String"
            } ?: reportInternal(
            "can't locate kotlin.IllegalArgumentException(String) constructor"
        )
        return context.irBuilder(function).irSynthBody {
            val typeArgsParam = function.parameters.first { !it.isDispatchReceiver }
            // if (typeArgs.size != arity) throw IllegalArgumentException("...")
            val sizeExpr = irCall(sizeGetter).apply {
                dispatchReceiver = irGet(typeArgsParam)
            }
            val sizeCheck = irNotEquals(sizeExpr, irInt(arity))
            val messageIae = buildStringFormat(
                irString(
                    "$serviceName expects $arity type parameter(s), got "
                ),
                sizeExpr
            )
            +irIfThen(
                context.irBuiltIns.unitType,
                sizeCheck,
                irThrow(
                    irCallConstructor(iaeStringCtor.symbol, emptyList()).apply {
                        putArgs(messageIae)
                    }
                )
            )
            // Build serializer args: resolveSerializerOrThrow(typeArgs[i], serviceName) for each i
            val serializerArgs = List(arity) { i ->
                irCall(resolveFn).apply {
                    arguments[0] = irCall(listGet).apply {
                        dispatchReceiver = irGet(typeArgsParam)
                        arguments[1] = irInt(i)
                    }
                    arguments[1] = irString(serviceName)
                }
            }
            +irReturn(
                irCallConstructor(objConstructor.symbol, objTypeArgs).apply {
                    type = objClass.typeWith(objTypeArgs)
                    putArgs(*serializerArgs.toTypedArray())
                }
            )
        }
    }

    /**
     * Body for the generic companion's `operator fun <T> invoke(tSer, ...): RpcObject<Service<T>>`.
     * Instantiates the generated `Obj<T>` nested class with the serializers and returns it.
     */
    private fun buildInvokeFactoryBody(function: IrSimpleFunction, cls: ServiceClass): IrBody {
        val objClass = cls.irClass.declarations.filterIsInstance<IrClass>()
            .firstOrNull { it.name == FqConstants.OBJ }
            ?: reportInternal(
                "missing generated Obj class on ${cls.irClass.kotlinFqName.asString()} " +
                    "— FirKsrpcObjGenerator did not run or generated under a different name"
            )
        val objConstructor = objClass.constructors.first { it.isPrimary }
        val invokeTypeParams = function.typeParameters
        val typeArgs = invokeTypeParams.map { it.defaultType }
        return context.irBuilder(function).irSynthBody {
            +irReturn(
                irCallConstructor(objConstructor.symbol, typeArgs).apply {
                    type = objClass.typeWith(typeArgs)
                    val passthroughArgs = function.parameters
                        .filter { !it.isDispatchReceiver }
                        .map { irGet(it) }
                    putArgs(*passthroughArgs.toTypedArray())
                }
            )
        }
    }

    override fun generateBodyForConstructor(
        constructor: IrConstructor,
        key: GeneratedDeclarationKey?
    ): IrBody = context.irBuilder(constructor).irSynthBody { }
}
