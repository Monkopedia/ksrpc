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
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.common.lower.irThrow
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.backend.js.utils.isDispatchReceiver
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.declarations.addConstructor
import org.jetbrains.kotlin.ir.builders.declarations.addField
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irBranch
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irDelegatingConstructorCall
import org.jetbrains.kotlin.ir.builders.irElseBranch
import org.jetbrains.kotlin.ir.builders.irEquals
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irImplicitCast
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irSetField
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irVararg
import org.jetbrains.kotlin.ir.builders.irWhen
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.isMarkedNullable
import org.jetbrains.kotlin.ir.types.makeNotNull
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.SYNTHETIC_OFFSET
import org.jetbrains.kotlin.ir.util.addChild
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.createThisReceiverParameter
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.name.Name

/**
 * IR transformer for the generated `Obj<T, ...>` nested class on generic `@KsService`s.
 * This is the runtime RpcObject<Service<T, ...>> that users reach via
 * `Service(serializer)` (which is [FirCompanionDeclarationGenerator]'s `invoke`).
 */
class ObjGeneration(
    context: IrPluginContext,
    private val classes: MutableMap<String, ServiceClass>,
    private val env: KsrpcGenerationEnvironment
) : AbstractTransformerForGenerator(context) {

    override fun interestedIn(key: GeneratedDeclarationKey?): Boolean =
        key is FirKsrpcObjGenerator.Key

    override fun generateChildrenForClass(
        declaration: IrClass,
        key: GeneratedDeclarationKey?
    ): Collection<IrDeclaration> {
        val k = key as FirKsrpcObjGenerator.Key
        val cls = classes[k.target]
            ?: error("Invalid synthetic Obj for ${k.target}")
        val serializerFields = declaration.typeParameters.map { tp ->
            declaration.addField {
                startOffset = SYNTHETIC_OFFSET
                endOffset = SYNTHETIC_OFFSET
                name = Name.identifier(tp.name.asString() + "Serializer")
                origin = IrDeclarationOrigin.DELEGATE
                visibility = DescriptorVisibilities.PRIVATE
                type = env.kSerializer.typeWith(tp.defaultType)
                isFinal = true
                isStatic = false
            }
        }
        cls.setObjClass(declaration)
        cls.setObjSerializerFields(serializerFields)
        // Executors are pre-created by StubGeneration (parented to the Stub class) before
        // ObjGeneration runs. findEndpoint bodies look them up via `cls.genericExecutors`.
        check(cls.genericExecutors.isNotEmpty() || cls.methods.isEmpty()) {
            "Expected generic executors to be pre-created by StubGeneration for " +
                cls.irClass.kotlinFqName.asString()
        }
        // Eagerly emit property bodies for serviceName / endpoints so the IR visitor doesn't
        // need to rely on correspondingPropertySymbol being wired correctly across FIR2IR.
        val endpointsList = cls.methods.map { method ->
            (method.ksMethodAnnotation.arguments[0].constString() ?: "").trimStart('/')
        }
        val fqName = cls.irClass.kotlinFqName.asString()
        declaration.declarations.filterIsInstance<IrProperty>()
            .firstOrNull { it.name == FqConstants.SERVICE_NAME }
            ?.let { property ->
                property.getter?.body = context.irBuilder(property.getter!!).irSynthBody {
                    +irReturn(irString(fqName))
                }
            }
        declaration.declarations.filterIsInstance<IrProperty>()
            .firstOrNull { it.name == FqConstants.ENDPOINTS }
            ?.let { property ->
                property.getter?.body = context.irBuilder(property.getter!!).irSynthBody {
                    +irReturn(irListOfStrings(env, endpointsList))
                }
            }
        return emptyList()
    }

    override fun generateBodyForConstructor(
        constructor: IrConstructor,
        key: GeneratedDeclarationKey?
    ): IrBody? {
        val k = key as? FirKsrpcObjGenerator.Key ?: return null
        val cls = classes[k.target] ?: return null
        val objClass = constructor.parentAsClass
        val serializerFields = cls.objSerializerFields
        return context.irBuilder(constructor.symbol).irSynthBody {
            +irDelegatingConstructorCall(
                context.irBuiltIns.anyClass.owner.constructors.single()
            )
            val nonDispatch = constructor.parameters.filter { !it.isDispatchReceiver }
            for ((idx, field) in serializerFields.withIndex()) {
                +irSetField(irGet(objClass.thisReceiver!!), field, irGet(nonDispatch[idx]))
            }
        }
    }

    override fun generateBodyForFunction(
        function: IrSimpleFunction,
        key: GeneratedDeclarationKey?
    ): IrBody? {
        val k = key as? FirKsrpcObjGenerator.Key ?: return null
        val cls = classes[k.target] ?: return null
        function.correspondingPropertySymbol?.owner?.name?.let { propertyName ->
            if (propertyName == FqConstants.SERVICE_NAME) {
                return context.irBuilder(function).irSynthBody {
                    +irReturn(irString(cls.irClass.kotlinFqName.asString()))
                }
            }
            if (propertyName == FqConstants.ENDPOINTS) {
                val endpoints = cls.methods.map { method ->
                    (method.ksMethodAnnotation.arguments[0].constString() ?: "")
                        .trimStart('/')
                }
                return context.irBuilder(function).irSynthBody {
                    +irReturn(irListOfStrings(env, endpoints))
                }
            }
        }
        return when (function.name) {
            FqConstants.CREATE_STUB -> buildCreateStub(function, cls)
            FqConstants.FIND_ENDPOINT -> buildFindEndpoint(function, cls)
            else -> null
        }
    }

    private fun buildCreateStub(function: IrSimpleFunction, cls: ServiceClass): IrBlockBody {
        val stubConstructor = cls.stubConstructor
        val serializerFields = cls.objSerializerFields
        val objClass = function.parentAsClass
        return context.irBuilder(function).irSynthBody {
            val thisRcv = function.dispatchReceiverParameter!!
            val channelParam = function.parameters.first { !it.isDispatchReceiver }
            +irReturn(
                irCallConstructor(
                    stubConstructor.symbol,
                    objClass.typeParameters.map { it.defaultType }
                ).apply {
                    val args = mutableListOf<IrExpression>(irGet(channelParam))
                    for (field in serializerFields) {
                        args += irGetField(irGet(thisRcv), field)
                    }
                    putArgs(*args.toTypedArray())
                }
            )
        }
    }

    private fun buildFindEndpoint(function: IrSimpleFunction, cls: ServiceClass): IrBlockBody {
        val thisRcv = function.dispatchReceiverParameter!!
        val builder = GenericMethodIrBuilder(context, env, cls)
        return context.irBuilder(function).irSynthBody {
            val input = function.parameters.first { !it.isDispatchReceiver }
            val branches = buildList {
                for (method in cls.methods) {
                    val endpoint = method.ksMethodAnnotation.arguments[0].constString()
                        ?: error("Lost endpoint")
                    val executor = cls.genericExecutors[endpoint.trimStart('/')]
                        ?: error("Executor missing for endpoint $endpoint")
                    this += irBranch(
                        irEquals(irString(endpoint.trimStart('/')), irGet(input)),
                        builder.irCreateRpcMethod(
                            this@irSynthBody,
                            thisRcv,
                            cls.objSerializerFields,
                            endpoint,
                            method.function,
                            method.metadataAnnotations,
                            executor = executor
                        )
                    )
                }
                val message = buildStringFormat(
                    irString("Unknown endpoint: "),
                    irGet(input),
                    irString(" in service " + cls.irClass.kotlinFqName.asString())
                )
                this += irElseBranch(
                    irThrow(
                        irCallConstructor(env.endpointStrConstructor, emptyList()).apply {
                            putArgs(message)
                        }
                    )
                )
            }
            +irReturn(irWhen(function.returnType, branches))
        }
    }
}

/**
 * Builds an RpcMethod constructor-call IR expression for a method on a generic service,
 * resolving any type-parameter-bearing serializers via the given instance fields on
 * some receiver (either an Obj instance or a Stub instance).
 */
internal class GenericMethodIrBuilder(
    private val context: IrPluginContext,
    private val env: KsrpcGenerationEnvironment,
    private val cls: ServiceClass
) {
    fun irCreateRpcMethod(
        builder: IrBuilderWithScope,
        serializerReceiver: IrValueParameter,
        serializerFields: List<IrField>,
        endpoint: String,
        method: IrSimpleFunction,
        metadataAnnotations: List<IrConstructorCall>,
        executor: IrClass
    ): IrConstructorCall {
        val inputType = method.parameters.first { !it.isDispatchReceiver }.type
        val outputType = method.returnType
        val serviceType = cls.irClass.typeWith(
            cls.irClass.typeParameters.map { it.defaultType }
        )
        val rpcMethodConstructor = env.rpcMethod.constructors.first()
        val supportsMetadata =
            env.metadataSupported && rpcMethodConstructor.owner.parameters.size >= 5
        val call = builder.irCallConstructor(
            rpcMethodConstructor,
            listOf(serviceType, inputType, outputType)
        ).apply {
            this.type = env.rpcMethod.typeWith(serviceType, inputType, outputType)
        }

        val inputConverter =
            createTypeConverter(builder, inputType, serializerReceiver, serializerFields)
        val outputConverter =
            createTypeConverter(builder, outputType, serializerReceiver, serializerFields)
        val executorInstance = builder.irCallConstructor(
            executor.constructors.first().symbol,
            emptyList()
        ).apply {
            type = executor.typeWith()
        }
        val args = mutableListOf<IrExpression>(
            builder.irString(endpoint.trimStart('/')),
            inputConverter,
            outputConverter,
            executorInstance
        )
        if (supportsMetadata) {
            val decl = DeclarationIrBuilder(
                context,
                method.symbol,
                SYNTHETIC_OFFSET,
                SYNTHETIC_OFFSET
            )
            args += MetadataIrBuilder(env, decl).buildMetadataList(metadataAnnotations)
        }
        call.putArgs(*args.toTypedArray())
        return call
    }

    private fun createTypeConverter(
        builder: IrBuilderWithScope,
        type: IrType,
        serializerReceiver: IrValueParameter,
        serializerFields: List<IrField>
    ): IrExpression {
        // BINARY (ByteReadChannel) transformer.
        if (type.classFqName == FqConstants.BYTE_READ_CHANNEL) {
            return builder.irGetObject(env.binaryTransformer)
        }
        // Subservice transforms are not supported for type-parameter-bearing output types,
        // because we'd need an RpcObject<Service<T>> which requires composing a companion
        // invoke. That's out of scope for the initial generic support. Fall through to the
        // serializer transformer and let the runtime fail loudly if this actually happens.
        return builder.irCallConstructor(
            env.serializerTransformer.constructors.first(),
            listOf(type)
        ).apply {
            this.type = env.serializerTransformer.typeWith(type)
            putArgs(buildSerializer(builder, type, serializerReceiver, serializerFields))
        }
    }

    private fun buildSerializer(
        builder: IrBuilderWithScope,
        type: IrType,
        serializerReceiver: IrValueParameter,
        serializerFields: List<IrField>
    ): IrExpression {
        val classifier = (type as? IrSimpleType)?.classifier
        val serviceTps = cls.irClass.typeParameters
        val tpIndex = if (classifier is org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol) {
            serviceTps.indexOfFirst { it.symbol == classifier }
        } else {
            -1
        }
        if (tpIndex >= 0 && tpIndex < serializerFields.size) {
            val baseExpr = builder.irGetField(
                builder.irGet(serializerReceiver),
                serializerFields[tpIndex]
            )
            return if (type.isMarkedNullable()) {
                wrapNullable(builder, baseExpr, type)
            } else {
                baseExpr
            }
        }
        // Fallback: use kotlinx.serialization.serializer<T>().
        val serializerFn = env.serializerMethod
            ?: error("Missing kotlinx.serialization.serializer<T>() reference")
        return builder.irCall(serializerFn).apply {
            typeArguments[0] = type
            this.type = env.kSerializer.typeWith(type)
        }
    }

    private fun wrapNullable(
        builder: IrBuilderWithScope,
        base: IrExpression,
        nullableType: IrType
    ): IrExpression {
        val getter = env.getSerializerNullable
            ?: error(
                "kotlinx.serialization's KSerializer.nullable extension is not on the " +
                    "compile classpath; nullable type-parameter serialization unsupported."
            )
        return builder.irCall(getter).apply {
            // Extension receiver is the base serializer. In 2.x IR, argument[0] is the
            // extension receiver for extension properties' getters.
            arguments[0] = base
            this.type = env.kSerializer.typeWith(nullableType.makeNotNull())
        }
    }

    fun buildExecutor(referencedFunction: IrSimpleFunction, container: IrClass): IrClass {
        val executorClass = context.irFactory.buildClass {
            startOffset = referencedFunction.startOffset
            endOffset = referencedFunction.endOffset
            name = Name.identifier(
                "Anonymous${referencedFunction.name.asString().replaceFirstChar { it.uppercase() }}"
            )
            visibility = DescriptorVisibilities.INTERNAL
            kind = ClassKind.CLASS
            modality = Modality.FINAL
            isCompanion = false
        }
        executorClass.parent = container
        executorClass.superTypes = listOf(env.serviceExecutor.typeWith())
        executorClass.createThisReceiverParameter()

        executorClass.addConstructor {
            startOffset = SYNTHETIC_OFFSET
            endOffset = SYNTHETIC_OFFSET
            isPrimary = true
        }.apply {
            body = context.irBuilder(symbol).irBlockBody {
                +irDelegatingConstructorCall(
                    context.irBuiltIns.anyClass.owner.constructors.single()
                )
            }
        }
        val invokeMethod = env.serviceExecutor.findMethod(FqConstants.INVOKE)
        val override = context.overrideMethod(
            executorClass,
            invokeMethod,
            referencedFunction.returnType
        ) { override ->
            +irReturn(
                irCall(referencedFunction).apply {
                    type = referencedFunction.returnType
                    putArgs(
                        irImplicitCast(
                            irGet(override.parameters[1]),
                            referencedFunction.dispatchReceiverParameter?.type!!
                        ),
                        irImplicitCast(
                            irGet(override.parameters[2]),
                            referencedFunction.parameters[1].type
                        )
                    )
                }
            )
        }
        executorClass.addChild(override)
        container.addChild(executorClass)
        return executorClass
    }
}

// Extract the irListOfStrings helper so both CompanionGeneration and ObjGeneration can share it.
internal fun IrBuilderWithScope.irListOfStrings(
    env: KsrpcGenerationEnvironment,
    values: List<String>
): IrExpression = irCall(env.listOfFunction).apply {
    typeArguments[0] = context.irBuiltIns.stringType
    val varargParameter = env.listOfFunction.owner.parameters
        .single { it.kind == org.jetbrains.kotlin.ir.declarations.IrParameterKind.Regular }
    arguments[varargParameter] = irVararg(
        context.irBuiltIns.stringType,
        values.map { irString(it) }
    )
}
