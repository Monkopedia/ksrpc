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

import com.monkopedia.ksrpc.plugin.FqConstants.BYTE_READ_CHANNEL
import com.monkopedia.ksrpc.plugin.FqConstants.INVOKE
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
            GenericMethodIrBuilder(context, env, service).also { builder ->
                for (serviceMethod in service.methods) {
                    val endpoint = serviceMethod.ksMethodAnnotation.arguments[0].constString()
                        ?: error("Lost endpoint")
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
                        ?: error("Lost endpoint")
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
        return context.overrideMethodWithSubstitution(
            this,
            method.symbol,
            substitutor,
            substitutedReturn
        ) { override ->
            val thisParam = override.parameters[0]
            val inputParam = override.parameters[1]
            val executor = service.genericExecutors[endpoint.trimStart('/')]
                ?: error("Executor missing for endpoint $endpoint")
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
                        irGet(inputParam)
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
            ?: error("Can't find close method")
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
        val endpoint = annotation.arguments[0].constString() ?: error("Lost endpoint")
        val methodField =
            generateRpcMethod(env, endpoint, method, this, companion, metadataAnnotations)

        service.addEndpoint(endpoint, methodField)
        val callChannel = env.rpcMethod.findMethod(FqConstants.CALL_CHANNEL)

        return context.overrideMethod(this, method.symbol) { override ->
            +irReturn(
                irCall(callChannel).apply {
                    type = method.returnType
                    if (override.parameters.size != 2) {
                        val overrideParams = override.parameters.map {
                            it.name.asString() to it.type.classFqName?.asString()
                        }
                        error("Unexpected override parameters $overrideParams")
                    }
                    putArgs(
                        irCall(methodField).apply {
                            putArgs(irGetObject(companion.symbol))
                        },
                        irGetField(irGet(override.parameters[0]), service.channel),
                        irGet(override.parameters[1])
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
        val inputType = method.parameters.first { !it.isDispatchReceiver }.type
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
                args += MetadataIrBuilder(env, this@irCreateRpcMethod)
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
        RpcType.BINARY -> declarationIrBuilder.irGetObject(env.binaryTransformer)

        RpcType.SERVICE -> declarationIrBuilder.irCallConstructor(
            env.subserviceTransformer.constructors.first(),
            listOf(outputType)
        ).apply {
            type = env.subserviceTransformer.typeWith(outputType)
            val companionSymbol = env.companionSymbol(outputType)
            putArgs(declarationIrBuilder.irGetObject(companionSymbol))
        }

        else -> declarationIrBuilder.irCallConstructor(
            env.serializerTransformer.constructors.first(),
            listOf(outputType)
        ).apply {
            type = env.serializerTransformer.typeWith(outputType)
            putArgs(getSerializer(declarationIrBuilder, outputType))
        }
    }

    private fun getSerializer(irBlockBodyBuilder: DeclarationIrBuilder, type: IrType) =
        irBlockBodyBuilder.irCall(env.serializerMethod ?: error("Missing serializer")).apply {
            typeArguments[0] = type
            this.type = env.kSerializer.typeWith(type)
        }

    private fun determineType(type: IrType): RpcType = when {
        type.extends(env.rpcService) -> RpcType.SERVICE
        type.classFqName == BYTE_READ_CHANNEL -> RpcType.BINARY
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
        SERVICE
    }
}

fun KsrpcGenerationEnvironment.companionSymbol(outputType: IrType): IrClassSymbol {
    val clazz = outputType.getClass()
    val companionSymbol = clazz?.companionObject()?.symbol
        ?: error("Missing companion ${outputType.classFqName?.asString()}")
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
