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
import org.jetbrains.kotlin.backend.common.lower.irIfThen
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.backend.js.utils.isDispatchReceiver
import org.jetbrains.kotlin.ir.builders.IrBlockBodyBuilder
import org.jetbrains.kotlin.ir.builders.declarations.addConstructor
import org.jetbrains.kotlin.ir.builders.declarations.addField
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.builders.declarations.addTypeParameter
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.builders.declarations.buildField
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.declarations.buildReceiverParameter
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irDelegatingConstructorCall
import org.jetbrains.kotlin.ir.builders.irEqualsNull
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irImplicitCast
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irSetField
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.getClass
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.types.starProjectedType
import org.jetbrains.kotlin.ir.types.typeOrFail
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.SYNTHETIC_OFFSET
import org.jetbrains.kotlin.ir.util.addChild
import org.jetbrains.kotlin.ir.util.companionObject
import org.jetbrains.kotlin.ir.util.constructedClass
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.createThisReceiverParameter
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.util.superTypes
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.util.capitalizeDecapitalize.capitalizeAsciiOnly

class StubGeneration(
    context: IrPluginContext,
    private val messageCollector: MessageCollector,
    private val classes: MutableMap<String, ServiceClass>,
    private val env: KsrpcGenerationEnvironment
) : AbstractTransformerForGenerator(context) {

    override fun interestedIn(key: GeneratedDeclarationKey?): Boolean =
        key is FirKsrpcStubGenerator.Key

    override fun generateChildrenForClass(
        declaration: IrClass,
        key: GeneratedDeclarationKey?
    ): Collection<IrDeclaration> {
        val target = (key as? FirKsrpcStubGenerator.Key)?.target
        val service = classes[target] ?: return emptyList()
        // For generic services, also add per-instance serializer fields on the Stub so
        // method bodies can reach the injected KSerializer<T>. We build these free-standing
        // (attach = false) so `generateChildrenForClass` can add them via its returned list.
        val stubSerializerFields = if (declaration.typeParameters.isNotEmpty()) {
            context.buildSerializerFieldsForTypeParams(declaration, env, attach = false)
        } else {
            emptyList()
        }
        service.setStubSerializerFields(stubSerializerFields)
        // For generic services, pre-create per-endpoint ServiceExecutor classes parented
        // to the Stub. They are shared with Obj (ObjGeneration looks them up via
        // `service.genericExecutors`) so both the stub body and Obj.findEndpoint produce
        // RpcMethod instances that reference the same executor class.
        val genericBuilder = if (declaration.typeParameters.isNotEmpty()) {
            GenericMethodIrBuilder(context, env, messageCollector, service).also { builder ->
                for (serviceMethod in service.methods) {
                    val endpoint = serviceMethod.ksMethodAnnotation.arguments[0].constString()
                        ?: reportInternal(
                            "@KsMethod annotation on " +
                                "${serviceMethod.function.name.asString()} lost its " +
                                "endpoint argument after validation"
                        )
                    val executor = builder.buildExecutor(serviceMethod.function, declaration)
                    service.genericExecutors[endpoint.trimStart('/')] = executor
                }
            }
        } else {
            null
        }
        return buildList {
            add(generateChannelField(service))
            addAll(stubSerializerFields)
            val companion = generateCompanion(service)
            add(companion)
            service.methods.map { serviceMethod ->
                if (genericBuilder != null) {
                    val endpoint = serviceMethod.ksMethodAnnotation.arguments[0].constString()
                        ?: reportInternal(
                            "@KsMethod annotation on " +
                                "${serviceMethod.function.name.asString()} lost its " +
                                "endpoint argument after validation"
                        )
                    add(
                        declaration.generateGenericMethodBody(
                            serviceMethod.function,
                            endpoint,
                            serviceMethod.metadataAnnotations,
                            service,
                            genericBuilder
                        )
                    )
                } else {
                    add(
                        declaration.generateMethodField(
                            serviceMethod.function,
                            serviceMethod.ksMethodAnnotation,
                            serviceMethod.metadataAnnotations,
                            companion,
                            service
                        )
                    )
                }
            }
            add(declaration.generateCloseMethod(service))
        }
    }

    /**
     * For generic services: emit a stub method body that creates a fresh `RpcMethod` inline
     * using the stub instance's serializer fields, then calls `rpcMethod.callChannel(...)`.
     * No caching — a fresh RpcMethod is materialized per call. This is a simpler contract
     * than the non-generic path's lazy-cached Stub.Companion fields and avoids having to
     * thread the per-instance serializer through a shared static cache.
     *
     * Parameter / return types use the STUB's type parameters (substituting for the service
     * type parameters). Without substitution, Kotlin/Native reports the override as an
     * abstract-function-not-implemented linkage error because the override's types reference
     * the service interface's type parameter symbols rather than the stub's.
     */
    private fun IrClass.generateGenericMethodBody(
        method: IrSimpleFunction,
        endpoint: String,
        metadataAnnotations: List<IrConstructorCall>,
        service: ServiceClass,
        builder: GenericMethodIrBuilder
    ): IrFunction {
        val callChannel = env.rpcMethod.findMethod(FqConstants.CALL_CHANNEL)
        val substitutor = stubTypeSubstitutor(service, this)
        val substitutedReturn = substitutor.substitute(method.returnType)
        val hasValueParam = method.parameters.any { !it.isDispatchReceiver }
        return context.overrideMethodWithSubstitution(
            this,
            method.symbol,
            substitutor,
            substitutedReturn
        ) { override ->
            val thisParam = override.parameters[0]
            // For 0-arg @KsMethod functions the override has only the dispatch receiver
            // and we pass Unit as the call input.
            val inputExpr: IrExpression = if (hasValueParam) {
                irGet(override.parameters[1])
            } else {
                irGetObject(context.irBuiltIns.unitClass)
            }
            val executor = service.genericExecutors[endpoint.trimStart('/')]
                ?: reportInternal(
                    "generic executor missing for endpoint $endpoint on " +
                        service.irClass.kotlinFqName.asString()
                )
            +irReturn(
                irCall(callChannel).apply {
                    type = substitutedReturn
                    putArgs(
                        builder.irCreateRpcMethod(
                            this@overrideMethodWithSubstitution,
                            thisParam,
                            service.stubSerializerFields,
                            endpoint,
                            method,
                            metadataAnnotations,
                            executor = executor
                        ),
                        irGetField(irGet(thisParam), service.channel),
                        inputExpr
                    )
                }
            )
        }
    }

    override fun generateBodyForFunction(
        function: IrSimpleFunction,
        key: GeneratedDeclarationKey?
    ): IrBody? = null

    override fun generateBodyForConstructor(
        constructor: IrConstructor,
        key: GeneratedDeclarationKey?
    ): IrBody? {
        val target = (key as? FirKsrpcStubGenerator.Key)?.target
        val service = classes[target] ?: return null
        service.setStubConstructor(constructor)
        return context.irBuilder(constructor.symbol).irSynthBody {
            +irDelegatingConstructorCall(
                context.irBuiltIns.anyClass.owner.constructors.single()
            )
            val cls = constructor.constructedClass
            val channelField = service.channel
            val nonDispatch = constructor.parameters.filter { !it.isDispatchReceiver }
            // Primary-constructor parameters are (channel, T1Serializer, T2Serializer, ...).
            +irSetField(irGet(cls.thisReceiver!!), channelField, irGet(nonDispatch[0]))
            for ((idx, field) in service.stubSerializerFields.withIndex()) {
                +irSetField(
                    irGet(cls.thisReceiver!!),
                    field,
                    irGet(nonDispatch[idx + 1])
                )
            }
        }
    }

    private fun generateChannelField(cls: ServiceClass) = irFactory.buildField {
        name = Name.identifier("channel")
        type = env.serializedService.starProjectedType
        visibility = DescriptorVisibilities.PRIVATE
        origin = IrDeclarationOrigin.DELEGATE
        isFinal = true
        startOffset = SYNTHETIC_OFFSET
        endOffset = SYNTHETIC_OFFSET
    }.also {
        cls.setChannel(it)
    }

    private fun generateCompanion(cls: ServiceClass) = context.irFactory.buildClass {
        this.startOffset = SYNTHETIC_OFFSET
        this.endOffset = SYNTHETIC_OFFSET
        this.name = Name.identifier("Companion")
        this.modality = Modality.FINAL
        this.visibility = DescriptorVisibilities.PUBLIC
        this.isCompanion = true
        this.kind = ClassKind.OBJECT
    }.apply {
        origin = IrDeclarationOrigin.DELEGATE
        annotations = listOf(createThreadLocalAnnotation())
        createThisReceiverParameter()
        addConstructor {
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
    }.also {
        cls.setStubCompanion(it)
    }

    private fun IrClass.createThreadLocalAnnotation() = context.irBuilder(symbol)
        .irCallConstructor(env.threadLocal.constructors.single(), listOf())

    private fun IrClass.generateCloseMethod(serviceClass: ServiceClass): IrSimpleFunction {
        val close = functions.find { it.name == FqConstants.CLOSE }
            ?: reportInternal(
                "can't find close() on generated Stub for " +
                    serviceClass.irClass.kotlinFqName.asString()
            )
        val suspendClose = env.suspendCloseable.findMethod(FqConstants.CLOSE)

        return context.overrideMethod(this, close.symbol, close.returnType) { override ->
            +irCall(suspendClose).apply {
                putArgs(
                    irGetField(irGet(override.dispatchReceiverParameter!!), serviceClass.channel)
                )
            }
        }
    }

    private fun IrClass.generateMethodField(
        method: IrSimpleFunction,
        annotation: IrConstructorCall,
        metadataAnnotations: List<IrConstructorCall>,
        companion: IrClass,
        service: ServiceClass
    ): IrFunction {
        val endpoint = annotation.arguments[0].constString() ?: reportInternal(
            "@KsMethod annotation on ${method.name.asString()} lost its endpoint " +
                "argument after validation"
        )
        val methodField =
            generateRpcMethod(env, endpoint, method, this, companion, metadataAnnotations)

        service.addEndpoint(endpoint, methodField)
        val callChannel = env.rpcMethod.findMethod(FqConstants.CALL_CHANNEL)

        val valueParams = method.parameters.filter { !it.isDispatchReceiver }
        return context.overrideMethod(this, method.symbol) { override ->
            +irReturn(
                irCall(callChannel).apply {
                    type = method.returnType
                    // Expected override params: dispatch receiver + 0 or 1 value
                    // parameters (0 for @KsMethod functions with no declared input).
                    val expectedSize = 1 + valueParams.size
                    if (override.parameters.size != expectedSize) {
                        val overrideParams = override.parameters.map {
                            it.name.asString() to it.type.classFqName?.asString()
                        }
                        reportInternal(
                            "generated stub override for ${method.name.asString()} has " +
                                "unexpected parameters $overrideParams (expected " +
                                "dispatch + $valueParams.size value parameter(s))"
                        )
                    }
                    val inputExpr: IrExpression = if (valueParams.isEmpty()) {
                        // 0-arg @KsMethod: synthesize Unit as the call input.
                        irGetObject(context.irBuiltIns.unitClass)
                    } else {
                        irGet(override.parameters[1])
                    }
                    putArgs(
                        irCall(methodField).apply {
                            putArgs(irGetObject(companion.symbol))
                        },
                        irGetField(irGet(override.parameters[0]), service.channel),
                        inputExpr
                    )
                }
            )
        }
    }

    private fun generateRpcMethod(
        env: KsrpcGenerationEnvironment,
        endpoint: String,
        method: IrSimpleFunction,
        serviceInterface: IrClass,
        companion: IrClass,
        metadataAnnotations: List<IrConstructorCall>
    ): IrFunction {
        val field = companion.addField {
            startOffset = SYNTHETIC_OFFSET
            endOffset = SYNTHETIC_OFFSET
            this.name = Name.identifier(method.name.asString().capitalizeAsciiOnly() + "Backing")
            origin = IrDeclarationOrigin.DELEGATE
            this.visibility = DescriptorVisibilities.PRIVATE
            this.type = env.rpcMethod.starProjectedType.makeNullable()
            this.isFinal = false
            this.isStatic = false
        }
        return companion.addFunction {
            startOffset = SYNTHETIC_OFFSET
            endOffset = SYNTHETIC_OFFSET
            this.name = Name.identifier(method.name.asString().capitalizeAsciiOnly())
            this.origin = IrDeclarationOrigin.DELEGATE
            this.visibility = DescriptorVisibilities.INTERNAL
            this.returnType = env.rpcMethod.starProjectedType
        }.apply {
            parameters += buildReceiverParameter {
                this.type = companion.typeWith()
            }
            val builder = context.irBuilder(symbol)
            body = builder.irBlockBody {
                +irIfThen(
                    irEqualsNull(irGetField(irGet(dispatchReceiverParameter!!), field)),
                    irSetField(
                        irGet(dispatchReceiverParameter!!),
                        field,
                        builder.irCreateRpcMethod(
                            serviceInterface,
                            endpoint,
                            method,
                            metadataAnnotations
                        )
                    )
                )
                +irReturn(irGetField(irGet(dispatchReceiverParameter!!), field))
            }
        }
    }

    private fun DeclarationIrBuilder.irCreateRpcMethod(
        serviceInterface: IrClass,
        endpoint: String,
        method: IrSimpleFunction,
        metadataAnnotations: List<IrConstructorCall>
    ): IrConstructorCall {
        // 0-arg @KsMethod functions have no user-declared input; fall back to Unit
        // so the generated RpcMethod is shaped as `RpcMethod<Service, Unit, Out>`.
        val inputType = method.parameters.firstOrNull { !it.isDispatchReceiver }?.type
            ?: context.irBuiltIns.unitType
        val outputType = method.returnType
        val inputRpcType = determineType(inputType)
        val outputRpcType = determineType(outputType)

        val constructor = env.rpcMethod.constructors.first()
        // Back-compat: if the ksrpc-core on the compile classpath predates #11
        // (four-arg RpcMethod), fall back to the old call shape. When
        // MethodMetadata is available, always emit the five-arg shape and pass
        // `emptyList()` when there is no metadata.
        val supportsMetadata = env.metadataSupported && constructor.owner.parameters.size >= 5
        return irCallConstructor(
            constructor,
            listOf(serviceInterface.typeWith(), inputType, outputType)
        ).apply {
            this.type = env.rpcMethod.typeWith(serviceInterface.typeWith(), inputType, outputType)
            val args = mutableListOf(
                irString(endpoint.trimStart('/')),
                createTypeConverter(inputRpcType, inputType, this@irCreateRpcMethod),
                createTypeConverter(outputRpcType, outputType, this@irCreateRpcMethod),
                irBlock {
                    val createServiceExecutor =
                        this@StubGeneration.context
                            .buildAnonymousServiceExecutor(env, method, serviceInterface)
                    +irCallConstructor(
                        createServiceExecutor.constructors.first().symbol,
                        emptyList()
                    ).apply {
                        this.type = createServiceExecutor.typeWith()
                    }
                }
            )
            if (supportsMetadata) {
                args += MetadataIrBuilder(env, this@irCreateRpcMethod, messageCollector)
                    .buildMetadataList(metadataAnnotations)
            }
            putArgs(*args.toTypedArray())
        }
    }

    private fun createTypeConverter(
        outputRpcType: RpcType,
        outputType: IrType,
        declarationIrBuilder: DeclarationIrBuilder
    ) = when (outputRpcType) {
        // The paired `ObjGeneration.createTypeConverter` already emits a user
        // diagnostic when the underlying adapter is missing; by the time we
        // reach stub generation the classpath must satisfy it. Dispatch via
        // the binary-adapter registry to pick the correct transformer object.
        RpcType.BINARY -> {
            val adapter = env.findAdapterByFqName(outputType.classFqName)
                ?: reportInternal(
                    "RpcType.BINARY fired for unknown user type " +
                        "${outputType.classFqName?.asString()} — registry and " +
                        "determineType must agree"
                )
            val transformer = adapter.transformerClass
                ?: reportInternal(
                    "${adapter.userFqName.asString()} adapter (${adapter.moduleHint}) " +
                        "missing on the compile classpath — ObjGeneration should have " +
                        "reported the user-facing diagnostic before reaching stub codegen"
                )
            declarationIrBuilder.irGetObject(transformer)
        }

        RpcType.FLOW -> buildFlowTransformer(outputType, declarationIrBuilder)

        RpcType.SERVICE -> declarationIrBuilder.irCallConstructor(
            env.subserviceTransformer.constructors.first(),
            listOf(outputType)
        ).apply {
            type = env.subserviceTransformer.typeWith(outputType)
            putArgs(buildNonGenericSubserviceRpcObject(outputType, declarationIrBuilder))
        }

        else -> declarationIrBuilder.irCallConstructor(
            env.serializerTransformer.constructors.first(),
            listOf(outputType)
        ).apply {
            type = env.serializerTransformer.typeWith(outputType)
            putArgs(getSerializer(declarationIrBuilder, outputType))
        }
    }

    /**
     * Emit `FlowTransformer<T>(KsFlowService.Obj<T>(Tser))` for a `Flow<T>` parameter or
     * return type. The constructed `KsFlowService.Obj<T>` is the generic sub-service's
     * compiler-generated `RpcObject` factory instance — mirrors
     * [GenericMethodIrBuilder.buildSubserviceRpcObject] for the generic service path.
     *
     * Only reachable when [KsrpcGenerationEnvironment.flowSupported] — callers guard via
     * [determineType] returning [RpcType.FLOW].
     */
    private fun buildFlowTransformer(
        flowType: IrType,
        declarationIrBuilder: DeclarationIrBuilder
    ): IrExpression {
        val elementType = (flowType as? org.jetbrains.kotlin.ir.types.IrSimpleType)
            ?.arguments
            ?.singleOrNull()
            ?.typeOrFail
            ?: reportInternal(
                "Flow<T> type ${flowType.classFqName?.asString()} has no type argument — " +
                    "cannot emit FlowTransformer"
            )
        val flowTransformerSymbol = env.flowTransformer
            ?: reportInternal(
                "Flow detection fired despite flowSupported=false " +
                    "(FlowTransformer symbol missing)"
            )
        val ksFlowServiceSymbol = env.ksFlowService
            ?: reportInternal(
                "Flow detection fired despite flowSupported=false " +
                    "(KsFlowService symbol missing)"
            )
        val rpcObjectExpr = buildKsFlowServiceRpcObject(
            declarationIrBuilder,
            ksFlowServiceSymbol,
            elementType
        )
        return declarationIrBuilder.irCallConstructor(
            flowTransformerSymbol.constructors.first(),
            listOf(elementType)
        ).apply {
            type = flowTransformerSymbol.typeWith(elementType)
            putArgs(rpcObjectExpr)
        }
    }

    /**
     * Build `KsFlowService.Obj<T>(serializer<T>())`.
     */
    private fun buildKsFlowServiceRpcObject(
        builder: DeclarationIrBuilder,
        ksFlowServiceSymbol: IrClassSymbol,
        elementType: IrType
    ): IrExpression {
        val objClass = ksFlowServiceSymbol.owner.declarations
            .filterIsInstance<IrClass>()
            .firstOrNull { it.name == FqConstants.OBJ }
            ?: reportInternal(
                "KsFlowService has no nested Obj class — the ksrpc compiler plugin " +
                    "must have processed ksrpc-flow"
            )
        val objConstructor = objClass.constructors.first { it.isPrimary }
        return builder.irCallConstructor(objConstructor.symbol, listOf(elementType)).apply {
            type = objClass.typeWith(elementType)
            putArgs(getSerializer(builder, elementType))
        }
    }

    /**
     * Build an `RpcObject<Sub>` IR expression for a sub-service return/parameter on a
     * non-generic outer service.
     *
     * - If the sub-service has no type parameters, its companion IS already an
     *   `RpcObject<Sub>` and we emit `irGetObject(Sub.Companion)`.
     * - Otherwise (the sub-service is generic, e.g. `KsFlowService<Update>` returned
     *   by a non-generic service method), we construct `Sub.Obj<TArgs>(serializers)`
     *   using `kotlinx.serialization.serializer<T>()` per type argument — since the
     *   outer service is non-generic, every type argument is concrete and reified
     *   lookup works. Mirrors [GenericMethodIrBuilder.buildSubserviceRpcObject] for
     *   the non-generic outer path.
     */
    private fun buildNonGenericSubserviceRpcObject(
        type: IrType,
        builder: DeclarationIrBuilder
    ): IrExpression {
        val simple = type as? org.jetbrains.kotlin.ir.types.IrSimpleType
            ?: return builder.irGetObject(env.companionSymbol(type))
        val subClassSymbol = simple.classifier as? IrClassSymbol
        val subClass = subClassSymbol?.owner
            ?: return builder.irGetObject(env.companionSymbol(type))
        if (subClass.typeParameters.isEmpty()) {
            return builder.irGetObject(env.companionSymbol(type))
        }
        val objClass = subClass.declarations
            .filterIsInstance<IrClass>()
            .firstOrNull { it.name == FqConstants.OBJ }
            ?: reportInternal(
                "generic sub-service ${subClass.kotlinFqName.asString()} has no nested Obj " +
                    "class — @KsService plugin did not run on the sub-service"
            )
        val objConstructor = objClass.constructors.first { it.isPrimary }
        val typeArgs = simple.arguments.map { it.typeOrFail }
        return builder.irCallConstructor(objConstructor.symbol, typeArgs).apply {
            this.type = objClass.typeWith(typeArgs)
            val serArgs = typeArgs.map { getSerializer(builder, it) }.toTypedArray()
            putArgs(*serArgs)
        }
    }

    private fun getSerializer(irBlockBodyBuilder: DeclarationIrBuilder, type: IrType) =
        irBlockBodyBuilder.irCall(
            env.serializerMethod ?: reportInternal(
                "can't resolve kotlinx.serialization.serializer<T>() — " +
                    "kotlinx-serialization-core must be on the compile classpath"
            )
        ).apply {
            typeArguments[0] = type
            this.type = env.kSerializer.typeWith(type)
        }

    private fun determineType(type: IrType): RpcType = when {
        env.flowSupported && type.classFqName == FqConstants.FLOW -> RpcType.FLOW
        type.extends(env.rpcService) -> RpcType.SERVICE
        env.findAdapterByFqName(type.classFqName) != null -> RpcType.BINARY
        else -> RpcType.DEFAULT
    }

    private fun IrType.extends(cls: IrClassSymbol): Boolean {
        if (classFqName == cls.owner.kotlinFqName) {
            return true
        }
        return superTypes().any { t ->
            t.extends(cls)
        }
    }

    private enum class RpcType {
        DEFAULT,
        BINARY,
        SERVICE,
        FLOW
    }
}

fun KsrpcGenerationEnvironment.companionSymbol(outputType: IrType): IrClassSymbol {
    val clazz = outputType.getClass()
    val companionSymbol = clazz?.companionObject()?.symbol
        // TODO (issue #27 follow-up): this can fire when a user returns an RpcService
        // subtype that isn't annotated `@KsService`. Route through MessageCollector once
        // StubGeneration.companionSymbol gets a location for the offending return type.
        ?: error(
            "ksrpc internal: missing companion on sub-service return type " +
                "${outputType.classFqName?.asString()} — the return type must be an " +
                "@KsService interface"
        )
    return companionSymbol
}

/**
 * Build an IrTypeSubstitutor that maps the service interface's class-level type parameters
 * to the Stub's class-level type parameters (positionally). Used to substitute types when
 * reconstructing stub overrides for generic services.
 */
fun stubTypeSubstitutor(
    service: ServiceClass,
    stub: IrClass
): org.jetbrains.kotlin.ir.types.IrTypeSubstitutor {
    val serviceTps = service.irClass.typeParameters.map { it.symbol }
    val stubTps = stub.typeParameters.map {
        it.defaultType as org.jetbrains.kotlin.ir.types.IrTypeArgument
    }
    return org.jetbrains.kotlin.ir.types.IrTypeSubstitutor(serviceTps, stubTps, false)
}

inline fun IrPluginContext.overrideMethodWithSubstitution(
    irClass: IrClass,
    method: IrSimpleFunctionSymbol,
    substitutor: org.jetbrains.kotlin.ir.types.IrTypeSubstitutor,
    returnType: IrType,
    body: IrBlockBodyBuilder.(IrSimpleFunction) -> Unit
) = irFactory.buildFun {
    this.name = method.owner.name
    this.returnType = returnType
    this.modality = Modality.FINAL
    this.visibility = method.owner.visibility
    this.isSuspend = method.owner.isSuspend
    this.isFakeOverride = false
    this.isInline = false
    this.origin = IrDeclarationOrigin.DELEGATE
}.apply {
    val thisReceiver = irClass.thisReceiver!!
    parameters += buildReceiverParameter {
        type = thisReceiver.type
    }
    val baseFqName = method.owner.parentAsClass.fqNameWhenAvailable!!
    overriddenSymbols = (irClass.superTypes.mapNotNull { it.classOrNull?.owner } + irClass)
        .filter { superType ->
            superType.isSubclassOfFqName(baseFqName.asString())
        }.flatMap { superClass ->
            superClass.functions
        }.filter { function ->
            function.name.asString() == method.owner.name.asString() &&
                function.overridesFunctionIn(baseFqName)
        }.map { it.symbol }
        .toList()
    method.owner.parameters.filter { !it.isDispatchReceiver }.forEach {
        addValueParameter {
            startOffset = SYNTHETIC_OFFSET
            endOffset = SYNTHETIC_OFFSET
            name = it.name
            type = substitutor.substitute(it.type)
            isHidden = it.isHidden
            isAssignable = it.isAssignable
            isCrossInline = it.isCrossinline
            varargElementType = it.varargElementType
        }
    }
    this.body = irBuilder(symbol).irBlockBody {
        body(this@apply)
    }
}

inline fun IrPluginContext.overrideMethod(
    irClass: IrClass,
    method: IrSimpleFunctionSymbol,
    returnType: IrType = method.owner.returnType,
    body: IrBlockBodyBuilder.(IrSimpleFunction) -> Unit
) = irFactory.buildFun {
    this.name = method.owner.name
    this.returnType = returnType
    this.modality = Modality.FINAL
    this.visibility = method.owner.visibility
    this.isSuspend = method.owner.isSuspend
    this.isFakeOverride = false
    this.isInline = false
    this.origin = IrDeclarationOrigin.DELEGATE
}.apply {
    val thisReceiver = irClass.thisReceiver!!
    parameters += buildReceiverParameter {
        type = thisReceiver.type
    }
    val baseFqName = method.owner.parentAsClass.fqNameWhenAvailable!!
    overriddenSymbols = (irClass.superTypes.mapNotNull { it.classOrNull?.owner } + irClass)
        .filter { superType ->
            superType.isSubclassOfFqName(baseFqName.asString())
        }.flatMap { superClass ->
            superClass.functions
        }.filter { function ->
            function.name.asString() == method.owner.name.asString() &&
                function.overridesFunctionIn(baseFqName)
        }.map { it.symbol }
        .toList()
    method.owner.typeParameters.forEach {
        addTypeParameter {
            startOffset = SYNTHETIC_OFFSET
            endOffset = SYNTHETIC_OFFSET
            index = it.index
            isReified = it.isReified
            superTypes.addAll(it.superTypes)
            variance = it.variance
            name = it.name
        }
    }
    method.owner.parameters.filter { !it.isDispatchReceiver }.forEach {
        addValueParameter {
            startOffset = SYNTHETIC_OFFSET
            endOffset = SYNTHETIC_OFFSET
            name = it.name
            type = it.type
            isHidden = it.isHidden
            isAssignable = it.isAssignable
            isCrossInline = it.isCrossinline
            varargElementType = it.varargElementType
        }
    }
    this.body = irBuilder(symbol).irBlockBody {
        body(this@apply)
    }
}
