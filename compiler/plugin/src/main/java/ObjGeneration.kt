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
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
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
import org.jetbrains.kotlin.ir.types.typeOrFail
import org.jetbrains.kotlin.ir.types.typeOrNull
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.SYNTHETIC_OFFSET
import org.jetbrains.kotlin.ir.util.addChild
import org.jetbrains.kotlin.ir.util.companionObject
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.createThisReceiverParameter
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.name.Name

/**
 * IR transformer for the generated `Obj<T, ...>` nested class on generic `@KsService`s.
 * This is the runtime RpcObject<Service<T, ...>> that users reach via
 * `Service(serializer)` (which is [FirCompanionDeclarationGenerator]'s `invoke`).
 */
class ObjGeneration(
    context: IrPluginContext,
    private val report: MessageCollector,
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
        // Class may have been removed by validation (issue #45 — @KsService subtype of
        // another @KsService). Errors are already reported; skip to avoid crashing.
        val cls = classes[k.target] ?: return emptyList()
        val serializerFields =
            context.buildSerializerFieldsForTypeParams(declaration, env, attach = true)
        cls.setObjClass(declaration)
        cls.setObjSerializerFields(serializerFields)
        // Executors are pre-created by StubGeneration (parented to the Stub class) before
        // ObjGeneration runs. findEndpoint bodies look them up via `cls.genericExecutors`.
        check(cls.genericExecutors.isNotEmpty() || cls.methods.isEmpty()) {
            "ksrpc internal: expected generic executors to be pre-created by " +
                "StubGeneration for ${cls.irClass.kotlinFqName.asString()}"
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
        val builder = GenericMethodIrBuilder(context, env, report, cls)
        return context.irBuilder(function).irSynthBody {
            val input = function.parameters.first { !it.isDispatchReceiver }
            val branches = buildList {
                for (method in cls.methods) {
                    val endpoint = method.ksMethodAnnotation.arguments[0].constString()
                        ?: reportInternal(
                            "@KsMethod annotation on " +
                                "${method.function.name.asString()} lost its endpoint " +
                                "argument after validation"
                        )
                    val executor = cls.genericExecutors[endpoint.trimStart('/')]
                        ?: reportInternal(
                            "generic executor missing for endpoint $endpoint on " +
                                cls.irClass.kotlinFqName.asString() +
                                " — StubGeneration should have pre-created it"
                        )
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
    private val report: MessageCollector,
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
        // 0-arg @KsMethod functions fall back to Unit as the input type.
        val inputType = method.parameters.firstOrNull { !it.isDispatchReceiver }?.type
            ?: context.irBuiltIns.unitType
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
            createTypeConverter(builder, inputType, serializerReceiver, serializerFields, method)
        val outputConverter =
            createTypeConverter(builder, outputType, serializerReceiver, serializerFields, method)
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
            args += MetadataIrBuilder(env, decl, report).buildMetadataList(metadataAnnotations)
        }
        call.putArgs(*args.toTypedArray())
        return call
    }

    private fun createTypeConverter(
        builder: IrBuilderWithScope,
        type: IrType,
        serializerReceiver: IrValueParameter,
        serializerFields: List<IrField>,
        method: IrSimpleFunction
    ): IrExpression {
        // BINARY (ByteReadChannel) transformer. Emits the
        // `ByteReadChannelTransformer` from `ksrpc-binary-ktor` — future
        // versions will dispatch via the adapter registry planned in #75.
        if (type.classFqName == FqConstants.BYTE_READ_CHANNEL) {
            val binaryTransformer = env.binaryTransformer ?: run {
                report.reportUserError(
                    "ByteReadChannel in @KsMethod requires `ksrpc-binary-ktor` " +
                        "on the compile classpath for " +
                        "${cls.irClass.kotlinFqName.asString()}.${method.name.asString()}",
                    element = method
                )
                // Fall back to `Unit` serializer transformer so codegen doesn't crash
                // after reporting the diagnostic.
                return builder.irCallConstructor(
                    env.serializerTransformer.constructors.first(),
                    listOf(context.irBuiltIns.unitType)
                ).apply {
                    this.type = env.serializerTransformer.typeWith(context.irBuiltIns.unitType)
                }
            }
            return builder.irGetObject(binaryTransformer)
        }
        // Flow<T> is bridged to the KsFlowService sub-service protocol. Only reachable when
        // ksrpc-flow is on the compile classpath — otherwise we fall through to the generic
        // serializer path, which will produce a clearer "no serializer" diagnostic.
        if (env.flowSupported && type.classFqName == FqConstants.FLOW) {
            return buildFlowTransformer(
                builder,
                type,
                serializerReceiver,
                serializerFields,
                method
            )
        }
        // Sub-service transformer when `type` is itself an @KsService. Both non-generic
        // sub-services (companion is the `RpcObject`) and generic sub-services (companion
        // is an `RpcObjectFactory`, and we materialize a concrete `Obj<T, ...>` via the
        // nested Obj constructor threading the outer service's serializer fields) are
        // supported.
        if (type.extendsRpcService()) {
            val rpcObjectExpr = buildSubserviceRpcObject(
                builder,
                type,
                serializerReceiver,
                serializerFields,
                method
            )
            return builder.irCallConstructor(
                env.subserviceTransformer.constructors.first(),
                listOf(type)
            ).apply {
                this.type = env.subserviceTransformer.typeWith(type)
                putArgs(rpcObjectExpr)
            }
        }
        return builder.irCallConstructor(
            env.serializerTransformer.constructors.first(),
            listOf(type)
        ).apply {
            this.type = env.serializerTransformer.typeWith(type)
            putArgs(buildSerializer(builder, type, serializerReceiver, serializerFields, method))
        }
    }

    /** True iff [this] has `com.monkopedia.ksrpc.RpcService` anywhere in its supertype chain. */
    private fun IrType.extendsRpcService(): Boolean {
        if (classFqName == FqConstants.FQRPC_SERVICE) return true
        val cls = (this as? IrSimpleType)?.classifier as?
            org.jetbrains.kotlin.ir.symbols.IrClassSymbol
        return cls?.owner?.superTypes?.any { it.extendsRpcService() } == true
    }

    /**
     * Build an IR expression that evaluates to an `RpcObject<ServiceType>` for a sub-service
     * parameter/return type. When the sub-service type has no type arguments that reference
     * our outer service's type parameters, we emit `irGetObject(subservice.Companion)`
     * (companion is already an `RpcObject<Sub>` for non-generic services, or assignable to
     * one when the sub-service itself is non-generic). Otherwise we construct the nested
     * `Sub.Obj<A, B, ...>(serA, serB, ...)` directly, composing each type-argument
     * serializer via [buildSerializer].
     */
    private fun buildSubserviceRpcObject(
        builder: IrBuilderWithScope,
        type: IrType,
        serializerReceiver: IrValueParameter,
        serializerFields: List<IrField>,
        method: IrSimpleFunction
    ): IrExpression {
        val simple = type as? IrSimpleType
            ?: reportInternal(
                "sub-service type ${type.render()} is not IrSimpleType — cannot build " +
                    "RpcObject for generic service codegen"
            )
        val subClassSymbol = simple.classifier as?
            org.jetbrains.kotlin.ir.symbols.IrClassSymbol
        val subClass = subClassSymbol?.owner ?: reportInternal(
            "sub-service type ${type.render()} has no class classifier — cannot " +
                "build RpcObject"
        )
        val companion = subClass.companionObject()
            ?: reportInternal(
                "sub-service ${subClass.kotlinFqName.asString()} has no companion — " +
                    "must be an @KsService interface"
            )
        // No class-level type parameters: companion IS an RpcObject<Sub>.
        if (subClass.typeParameters.isEmpty()) {
            return builder.irGetObject(companion.symbol)
        }
        // Generic sub-service — find its nested Obj class and construct it with
        // per-type-argument serializers composed from our serializer fields.
        val objClass = subClass.declarations
            .filterIsInstance<IrClass>()
            .firstOrNull { it.name == FqConstants.OBJ }
            ?: reportInternal(
                "sub-service ${subClass.kotlinFqName.asString()} has no nested Obj class — " +
                    "@KsService plugin did not run on the sub-service"
            )
        val objConstructor = objClass.constructors.first { it.isPrimary }
        val typeArgs = simple.arguments.map { it.typeOrFail }
        return builder.irCallConstructor(objConstructor.symbol, typeArgs).apply {
            this.type = objClass.typeWith(typeArgs)
            val serArgs = typeArgs.map { arg ->
                buildSerializer(builder, arg, serializerReceiver, serializerFields, method)
            }.toTypedArray()
            putArgs(*serArgs)
        }
    }

    /**
     * Recursively compose a `KSerializer<type>` IR expression.
     *
     * - Bare service-level type parameter (`T`): emit a field read against the injected
     *   `KSerializer<T>` on [serializerReceiver].
     * - `List<X>` / `Set<X>`: emit `ListSerializer(...)` / `SetSerializer(...)` with a
     *   recursively built element serializer.
     * - `Map<K, V>`: emit `MapSerializer(keySer, valueSer)` with recursively built arg
     *   serializers.
     * - Anything that doesn't transitively reference a class-level type parameter: fall
     *   back to the static `kotlinx.serialization.serializer<T>()` lookup — the stock
     *   behaviour already used by the non-generic codegen path.
     * - A type that references a type parameter but isn't one of the recognized wrapper
     *   shapes (e.g. a user-defined `@Serializable class Box<T>(val v: T)`): report a
     *   clear user error. Supporting user-defined generic wrappers is tracked as a
     *   follow-up on #44 and would require resolving the user type's serializer factory.
     *
     * Nullable wrapping is applied per layer — `List<T?>?` composes as
     * `ListSerializer(T.nullable).nullable`, matching kotlinx.serialization's own rules.
     */
    private fun buildSerializer(
        builder: IrBuilderWithScope,
        type: IrType,
        serializerReceiver: IrValueParameter,
        serializerFields: List<IrField>,
        method: IrSimpleFunction
    ): IrExpression {
        // If this type doesn't mention a class-level type parameter anywhere, we don't
        // need to compose anything — kotlinx.serialization's reified `serializer<T>()`
        // knows how to resolve it at runtime. This also matches how the non-generic
        // (all-concrete) path builds its transforms.
        if (!referencesTypeParameter(type)) {
            val serializerFn = env.serializerMethod
                ?: reportInternal(
                    "can't resolve kotlinx.serialization.serializer<T>() — " +
                        "kotlinx-serialization-core must be on the compile classpath"
                )
            return builder.irCall(serializerFn).apply {
                typeArguments[0] = type
                this.type = env.kSerializer.typeWith(type)
            }
        }

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

        // Recognized collection wrappers: List / Set / Map from kotlin.collections.
        val classFq = type.classFqName?.asString()
        val simple = type as? IrSimpleType
        val composed: IrExpression? = when (classFq) {
            "kotlin.collections.List" ->
                buildListSerializer(
                    builder,
                    simple,
                    serializerReceiver,
                    serializerFields,
                    method
                )

            "kotlin.collections.Set" ->
                buildSetSerializer(
                    builder,
                    simple,
                    serializerReceiver,
                    serializerFields,
                    method
                )

            "kotlin.collections.Map" ->
                buildMapSerializer(
                    builder,
                    simple,
                    serializerReceiver,
                    serializerFields,
                    method
                )

            else -> null
        }
        if (composed != null) {
            return if (type.isMarkedNullable()) {
                wrapNullable(builder, composed, type)
            } else {
                composed
            }
        }

        // A non-wrapper type that references a class-level type parameter — e.g.
        // `Box<T>` on a user-defined `@Serializable class Box<T>(...)`. We cannot
        // compose its serializer without resolving the user type's companion/factory,
        // which is out of scope for this change. Surface a clear user error at the
        // offending method.
        report.reportUserError(
            "Cannot compose a KSerializer for ${type.render()} on " +
                "${cls.irClass.kotlinFqName.asString()}.${method.name.asString()} — " +
                "only List/Set/Map wrappers of a service type parameter are supported. " +
                "Refactor to a concrete or supported wrapper type.",
            element = method
        )
        // Fall back to the static serializer<T>() call so codegen doesn't crash after
        // reporting the diagnostic; the user already sees an ERROR above.
        val serializerFn = env.serializerMethod
            ?: reportInternal(
                "can't resolve kotlinx.serialization.serializer<T>() — " +
                    "kotlinx-serialization-core must be on the compile classpath"
            )
        return builder.irCall(serializerFn).apply {
            typeArguments[0] = type
            this.type = env.kSerializer.typeWith(type)
        }
    }

    private fun buildListSerializer(
        builder: IrBuilderWithScope,
        simple: IrSimpleType?,
        serializerReceiver: IrValueParameter,
        serializerFields: List<IrField>,
        method: IrSimpleFunction
    ): IrExpression? {
        val elementType = simple?.arguments?.singleOrNull()?.typeOrFail ?: return null
        val listFn = env.listSerializerBuilder ?: run {
            report.reportUserError(
                "kotlinx.serialization.builtins.ListSerializer is not on the compile " +
                    "classpath; cannot compose a serializer for List<...> on " +
                    "${cls.irClass.kotlinFqName.asString()}.${method.name.asString()}",
                element = method
            )
            return null
        }
        val elementSer =
            buildSerializer(builder, elementType, serializerReceiver, serializerFields, method)
        return builder.irCall(listFn).apply {
            typeArguments[0] = elementType
            val listType = context.irBuiltIns.listClass.typeWith(elementType)
            this.type = env.kSerializer.typeWith(listType)
            arguments[0] = elementSer
        }
    }

    private fun buildSetSerializer(
        builder: IrBuilderWithScope,
        simple: IrSimpleType?,
        serializerReceiver: IrValueParameter,
        serializerFields: List<IrField>,
        method: IrSimpleFunction
    ): IrExpression? {
        val elementType = simple?.arguments?.singleOrNull()?.typeOrFail ?: return null
        val setFn = env.setSerializerBuilder ?: run {
            report.reportUserError(
                "kotlinx.serialization.builtins.SetSerializer is not on the compile " +
                    "classpath; cannot compose a serializer for Set<...> on " +
                    "${cls.irClass.kotlinFqName.asString()}.${method.name.asString()}",
                element = method
            )
            return null
        }
        val elementSer =
            buildSerializer(builder, elementType, serializerReceiver, serializerFields, method)
        return builder.irCall(setFn).apply {
            typeArguments[0] = elementType
            val setType = context.irBuiltIns.setClass.typeWith(elementType)
            this.type = env.kSerializer.typeWith(setType)
            arguments[0] = elementSer
        }
    }

    private fun buildMapSerializer(
        builder: IrBuilderWithScope,
        simple: IrSimpleType?,
        serializerReceiver: IrValueParameter,
        serializerFields: List<IrField>,
        method: IrSimpleFunction
    ): IrExpression? {
        val args = simple?.arguments ?: return null
        if (args.size != 2) return null
        val keyType = args[0].typeOrFail
        val valueType = args[1].typeOrFail
        val mapFn = env.mapSerializerBuilder ?: run {
            report.reportUserError(
                "kotlinx.serialization.builtins.MapSerializer is not on the compile " +
                    "classpath; cannot compose a serializer for Map<..., ...> on " +
                    "${cls.irClass.kotlinFqName.asString()}.${method.name.asString()}",
                element = method
            )
            return null
        }
        val keySer =
            buildSerializer(builder, keyType, serializerReceiver, serializerFields, method)
        val valueSer =
            buildSerializer(builder, valueType, serializerReceiver, serializerFields, method)
        return builder.irCall(mapFn).apply {
            typeArguments[0] = keyType
            typeArguments[1] = valueType
            val mapType = context.irBuiltIns.mapClass.typeWith(keyType, valueType)
            this.type = env.kSerializer.typeWith(mapType)
            arguments[0] = keySer
            arguments[1] = valueSer
        }
    }

    /**
     * True iff [type] transitively references one of [cls]'s class-level type parameters.
     * Used to short-circuit the recursive composer onto the static
     * `kotlinx.serialization.serializer<T>()` path for types that don't need any
     * per-instance serializer injection.
     */
    private fun referencesTypeParameter(type: IrType): Boolean {
        val simple = type as? IrSimpleType ?: return false
        val classifier = simple.classifier
        if (classifier is org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol) {
            return cls.irClass.typeParameters.any { it.symbol == classifier }
        }
        return simple.arguments.any { arg ->
            val argType = arg.typeOrNull ?: return@any false
            referencesTypeParameter(argType)
        }
    }

    private fun wrapNullable(
        builder: IrBuilderWithScope,
        base: IrExpression,
        nullableType: IrType
    ): IrExpression {
        val getter = env.getSerializerNullable
            ?: reportInternal(
                "kotlinx.serialization's KSerializer.nullable extension is not on the " +
                    "compile classpath; nullable type-parameter serialization unsupported"
            )
        return builder.irCall(getter).apply {
            // Extension receiver is the base serializer. In 2.x IR, argument[0] is the
            // extension receiver for extension properties' getters.
            arguments[0] = base
            this.type = env.kSerializer.typeWith(nullableType.makeNotNull())
        }
    }

    fun buildExecutor(referencedFunction: IrSimpleFunction, container: IrClass): IrClass =
        context.buildAnonymousServiceExecutor(env, referencedFunction, container)

    /**
     * Emit `FlowTransformer<T>(KsFlowService.Obj<T>(Tser))` for a `Flow<T>` parameter or
     * return type on a method belonging to a generic `@KsService`. The element type's
     * `KSerializer` is composed via [buildSerializer] so it can reference the outer
     * service's type parameters. Mirrors the non-generic path in
     * [StubGeneration.buildFlowTransformer] but threads the generic serializer
     * composition through.
     */
    private fun buildFlowTransformer(
        builder: IrBuilderWithScope,
        flowType: IrType,
        serializerReceiver: IrValueParameter,
        serializerFields: List<IrField>,
        method: IrSimpleFunction
    ): IrExpression {
        val simple = flowType as? IrSimpleType ?: reportInternal(
            "Flow<T> type ${flowType.render()} is not IrSimpleType"
        )
        val elementType = simple.arguments.singleOrNull()?.typeOrFail
            ?: reportInternal(
                "Flow<T> type ${flowType.render()} has no type argument — cannot emit " +
                    "FlowTransformer"
            )
        val flowTransformerSymbol = env.flowTransformer
            ?: reportInternal(
                "FlowTransformer symbol missing but Flow<T> detection fired — " +
                    "ksrpc-flow must be on the compile classpath"
            )
        val ksFlowServiceSymbol = env.ksFlowService
            ?: reportInternal(
                "KsFlowService symbol missing but Flow<T> detection fired — " +
                    "ksrpc-flow must be on the compile classpath"
            )
        val objClass = ksFlowServiceSymbol.owner.declarations
            .filterIsInstance<IrClass>()
            .firstOrNull { it.name == FqConstants.OBJ }
            ?: reportInternal(
                "KsFlowService has no nested Obj class — the ksrpc compiler plugin " +
                    "must have processed ksrpc-flow"
            )
        val objConstructor = objClass.constructors.first { it.isPrimary }
        val rpcObjectExpr = builder.irCallConstructor(
            objConstructor.symbol,
            listOf(elementType)
        ).apply {
            this.type = objClass.typeWith(elementType)
            putArgs(
                buildSerializer(builder, elementType, serializerReceiver, serializerFields, method)
            )
        }
        return builder.irCallConstructor(
            flowTransformerSymbol.constructors.first(),
            listOf(elementType)
        ).apply {
            this.type = flowTransformerSymbol.typeWith(elementType)
            putArgs(rpcObjectExpr)
        }
    }
}
