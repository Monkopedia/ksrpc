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

/*
 * Generation pipeline overview
 * ============================
 *
 * The ksrpc compiler plugin operates in two phases. FIR declaration generators synthesize
 * the public shape of the generated code (classes, members, signatures), then IR
 * transformers attach field definitions, fill in bodies and register behavior.
 *
 * FIR phase — declaration shape
 * -----------------------------
 *
 *   KsrpcComponentRegistrar wires FirKsrpcRegistrar, which registers four declaration
 *   generators (see FqConstants for FQ names):
 *
 *   - FirKsrpcStubGenerator           Emits a nested `Stub<T...>` class on every
 *                                     `@KsService` interface. The stub implements the
 *                                     service and holds the `SerializedService` channel
 *                                     plus one `KSerializer<Tn>` per service type param.
 *
 *   - FirCompanionDeclarationGenerator Emits a Companion object on every @KsService.
 *                                     For non-generic services the companion directly
 *                                     extends `RpcObject<Service>` and exposes
 *                                     `findEndpoint`/`createStub`/`serviceName`/`endpoints`.
 *                                     For generic services the companion instead extends
 *                                     `RpcObjectFactory<Service<*, ...>>` and exposes
 *                                     `operator fun <T...> invoke(serializers...)` plus
 *                                     `arity` and `create(typeArgs)`.
 *
 *   - FirKsrpcObjGenerator            Only runs for generic services. Emits a nested
 *                                     `Obj<T...>` class which implements
 *                                     `RpcObject<Service<T...>>`. Obj is what Companion's
 *                                     `invoke` returns and what `create` instantiates.
 *
 *   - FirKsrpcIntrospectionGenerator  Emits `getIntrospection()` on services that opt in
 *                                     via `@KsIntrospectable`.
 *
 *   Shared FIR helpers live in `FirGeneratorUtils.kt` (serializer value parameters,
 *   cone-type threading, kotlin.collections.List ClassId).
 *
 * IR phase — bodies and implementation
 * ------------------------------------
 *
 *   KsrpcIrGenerationExtension (this file) runs four transformers in order, each
 *   matching against the `GeneratedDeclarationKey` emitted by its FIR counterpart:
 *
 *   - StubGeneration         Attaches the channel field, per-TP serializer fields,
 *                            per-endpoint executor classes, method bodies that call
 *                            `RpcMethod.callChannel(...)`, and a `close()` override.
 *                            For non-generic services each method is backed by a lazy
 *                            RpcMethod on the Stub.Companion; for generic services we
 *                            emit a fresh `RpcMethod` per call, wiring in the stub
 *                            instance's serializer fields. Also pre-creates the
 *                            per-endpoint ServiceExecutor classes that ObjGeneration
 *                            later reuses via `ServiceClass.genericExecutors`.
 *
 *   - ObjGeneration          For generic services only: fills in Obj's serializer
 *                            fields, primary constructor body, `createStub`,
 *                            `findEndpoint`, and the `serviceName`/`endpoints`
 *                            properties. Emits RpcMethod constructor calls that resolve
 *                            type-parameter-bearing serializers from the Obj instance
 *                            fields (see [GenericMethodIrBuilder]).
 *
 *   - CompanionGeneration    Fills in `findEndpoint`, `createStub` (non-generic),
 *                            `invoke` and `create` (generic), plus the
 *                            `serviceName`/`endpoints`/`arity` property bodies and the
 *                            `@RpcObjectKey` annotation that points runtime dispatch
 *                            at this companion.
 *
 *   - IntrospectionGeneration Fills in `getIntrospection()` body.
 *
 *   Shared IR helpers live in `IrUtils.kt` (listOf<String>, serializer field creation,
 *   anonymous ServiceExecutor class, overrideMethod, etc.). Metadata propagation from
 *   sibling `@KsMethodMetadata` annotations is handled by `MetadataIrBuilder.kt`.
 *
 * Validation
 * ----------
 *
 *   [KsrpcIrGenerationExtension.validate] runs before transformation, rejecting
 *   services with non-RpcService supertypes, non-invariant class type parameters,
 *   method-level type parameters, duplicate endpoint names, `ByteReadChannel`-on-both-
 *   sides, and `@KsNotification` methods that don't return `Unit`. Diagnostics go
 *   through [PluginReporter.reportUserError], which resolves a source line/column
 *   from the offending IR element so the user sees a clean pointer at the declaration.
 *
 *   TODO: these validations currently run in the IR phase which limits IDE
 *   integration — a FIR-phase checker with `KtSourceElement` would produce a red
 *   squiggle directly on the offending element. Tracked as a 1.0+ follow-up.
 */

@file:OptIn(UnsafeDuringIrConstructionAPI::class)

package com.monkopedia.ksrpc.plugin

import com.monkopedia.ksrpc.plugin.FqConstants.BYTE_READ_CHANNEL
import com.monkopedia.ksrpc.plugin.FqConstants.FQRPC_SERVICE
import com.monkopedia.ksrpc.plugin.FqConstants.INTROSPECTABLE_RPC_SERVICE
import com.monkopedia.ksrpc.plugin.FqConstants.KS_SERVICE
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.backend.js.utils.isDispatchReceiver
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.types.Variance

class KsrpcIrGenerationExtension(private val report: MessageCollector) : IrGenerationExtension {

    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val classes = ServiceClass.findServices(report, moduleFragment)
        classes.values.removeIf { !validate(it) }

        val env = KsrpcGenerationEnvironment(pluginContext, report)
        val transformers = listOf(
            StubGeneration(pluginContext, report, classes, env),
            ObjGeneration(pluginContext, report, classes, env),
            CompanionGeneration(pluginContext, classes, env),
            IntrospectionGeneration(pluginContext, classes, env)
        )
        for (transformer in transformers) {
            moduleFragment.acceptChildrenVoid(transformer)
        }
    }

    private fun validate(cls: ServiceClass): Boolean {
        val names = mutableSetOf<String>()
        return cls.methods.fold(validateClass(cls.irClass)) { current, serviceMethod ->
            validateMethod(
                cls.irClass,
                serviceMethod.function,
                serviceMethod.ksMethodAnnotation,
                names
            ) && validateNotification(
                cls.irClass,
                serviceMethod
            ) && current
        }
    }

    private fun validateClass(irClass: IrClass): Boolean {
        var isValid = true
        val superTypeNames = irClass.superTypes.map { it.classFqName }
        val hasRpcServiceSuper = superTypeNames.contains(FQRPC_SERVICE)
        val hasIntrospectableSuper =
            superTypeNames.contains(INTROSPECTABLE_RPC_SERVICE.asSingleFqName())
        // Reject `@KsService` applied to a subtype of another `@KsService` interface.
        // The parent's @KsService annotation already drives codegen, and `rpcObject<Sub>()`
        // walks supertypes to find the parent's `RpcObject`/`RpcObjectFactory` companion
        // automatically. Allowing @KsService on the subtype would produce a duplicate
        // companion that has no working stub/obj of its own and previously crashed
        // CompanionGeneration with "Invalid synthetic declaration for ..." (issue #45).
        val ksServiceSuper = irClass.superTypes
            .mapNotNull { it.classOrNull?.owner }
            .firstOrNull { superClass ->
                superClass.annotations.any { it.type.classFqName == KS_SERVICE }
            }
        if (ksServiceSuper != null) {
            report.reportUserError(
                "@KsService cannot be applied to ${irClass.kotlinFqName.asString()} because " +
                    "its supertype ${ksServiceSuper.kotlinFqName.asString()} is already " +
                    "@KsService. Remove @KsService from " +
                    "${irClass.kotlinFqName.asString()}; rpcObject<" +
                    "${irClass.name.asString()}>() will find the parent's companion " +
                    "automatically.",
                element = irClass
            )
            isValid = false
        }
        if (!hasRpcServiceSuper && !hasIntrospectableSuper && ksServiceSuper == null) {
            report.reportUserError(
                "${irClass.kotlinFqName.asString()} does not extend ${FQRPC_SERVICE.asString()}",
                element = irClass
            )
            isValid = false
        }
        // Class-level type parameters on @KsService interfaces are supported, but must be
        // invariant. `out T`/`in T` requires serializer-variance handling that is not yet
        // implemented in codegen.
        for (tp in irClass.typeParameters) {
            if (tp.variance != Variance.INVARIANT) {
                report.reportUserError(
                    "${irClass.kotlinFqName.asString()}: type parameter ${tp.name.asString()} " +
                        "must be invariant (got ${tp.variance.label})",
                    element = tp
                )
                isValid = false
            }
        }
        return isValid
    }

    private fun validateMethod(
        irClass: IrClass,
        method: IrFunction,
        annotation: IrConstructorCall,
        names: MutableSet<String>
    ): Boolean {
        var isValid = true
        if (method.typeParameters.isNotEmpty()) {
            val fqName = irClass.kotlinFqName.asString()
            report.reportUserError(
                "$fqName.${method.name.asString()} cannot have type parameters",
                element = method
            )
            isValid = false
        }
        val valueParams = method.parameters.filter { !it.isDispatchReceiver }
        if (valueParams.size > 1) {
            val fqName = irClass.kotlinFqName.asString()
            report.reportUserError(
                "$fqName.${method.name.asString()} cannot have more than 1 parameter",
                element = method
            )
            isValid = false
        }
        val annotationArg = annotation.arguments[0]
        val name = annotationArg.constString()
        if (name == null) {
            val fqName = irClass.kotlinFqName.asString()
            val methodName = method.name.asString()
            report.reportUserError(
                "$fqName.$methodName: could not parse annotation argument $annotationArg",
                element = method
            )
            isValid = false
        } else if (!names.add(name)) {
            val fqName = irClass.kotlinFqName.asString()
            val methodName = method.name.asString()
            report.reportUserError(
                "$fqName.$methodName: cannot use endpoint $name, it has already been used",
                element = method
            )
            isValid = false
        }
        // valueParams may be empty for 0-arg @KsMethod functions; the RpcMethod is
        // generated with `Unit` as the input type in that case, so there is no
        // user-declared input to check against ByteReadChannel.
        val inputType = valueParams.firstOrNull()?.type?.classFqName
        val outputType = method.returnType.classFqName
        if (inputType == BYTE_READ_CHANNEL && outputType == BYTE_READ_CHANNEL) {
            val fqName = irClass.kotlinFqName.asString()
            val methodName = method.name.asString()
            report.reportUserError(
                "$fqName.$methodName: ByteReadChannel not yet supported for both input and output",
                element = method
            )
            isValid = false
        }
        return isValid
    }

    private fun validateNotification(irClass: IrClass, serviceMethod: ServiceMethod): Boolean {
        val hasNotification = serviceMethod.metadataAnnotations.any {
            it.type.classFqName == FqConstants.KS_NOTIFICATION
        }
        if (!hasNotification) return true
        val returnType = serviceMethod.function.returnType.classFqName
        if (returnType?.asString() != "kotlin.Unit") {
            val fqName = irClass.kotlinFqName.asString()
            val methodName = serviceMethod.function.name.asString()
            report.reportUserError(
                "$fqName.$methodName: @KsNotification methods must return Unit, " +
                    "but returns $returnType",
                element = serviceMethod.function
            )
            return false
        }
        return true
    }
}

fun IrExpression?.constString() = (this as? IrConst)?.value as? String
