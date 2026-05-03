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

import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.fqNameForIrSerialization
import org.jetbrains.kotlin.ir.util.getAllSuperclasses
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.FqName

private class Visitor(private val messageCollector: MessageCollector) : IrElementTransformerVoid() {
    val classes = mutableMapOf<String, ServiceClass>()
    private var currentService: ServiceClass? = null
    private val method = FqName("com.monkopedia.ksrpc.annotation.KsMethod")
    private val service = FqName("com.monkopedia.ksrpc.annotation.KsService")
    private val metadataMarker = FqConstants.KS_METHOD_METADATA
    private val ksError = FqConstants.KS_ERROR
    private val ksContext = FqConstants.KS_CONTEXT

    override fun visitClass(declaration: IrClass): IrStatement {
        val annotation = declaration.annotations.find { annotation ->
            annotation.type.classFqName == service
        }
        val lastService = currentService
        currentService = if (annotation != null) {
            ServiceClass(declaration)
        } else {
            null
        }
        val ret = super.visitClass(declaration)
        if (annotation != null) {
            classes[declaration.fqNameForIrSerialization.asString()] = currentService
                ?: reportInternal(
                    "lost currentService while visiting ${declaration.name.asString()}"
                )
        }
        currentService = lastService
        return ret
    }

    override fun visitSimpleFunction(declaration: IrSimpleFunction): IrStatement {
        super.visitFunction(declaration)
        val annotation = declaration.annotations.find { annotation ->
            annotation.type.classFqName == method
        }
        if (annotation != null) {
            val current = currentService
            if (current != null) {
                val metadataAnnotations = declaration.annotations.filter { siblingAnnotation ->
                    siblingAnnotation.type.classFqName != method &&
                        siblingAnnotation.type.classOrNull?.owner?.annotations?.any {
                            it.type.classFqName == metadataMarker
                        } == true
                }
                // Capture @KsError bindings. `@Repeatable` annotations may appear
                // either as individual entries or wrapped in a synthesized
                // Container class — preserve declaration order across either shape.
                val errorAnnotations = declaration.annotations.filter {
                    it.type.classFqName == ksError
                }
                // Capture @KsContext-meta-annotated annotations from both the
                // method and its enclosing @KsService class. Union of both.
                val isKsContextMeta = { ann: IrConstructorCall ->
                    ann.type.classOrNull?.owner?.annotations?.any {
                        it.type.classFqName == ksContext
                    } == true
                }
                val methodContextAnnotations = declaration.annotations
                    .filter(isKsContextMeta)
                val classContextAnnotations = currentService?.irClass?.annotations
                    ?.filter(isKsContextMeta) ?: emptyList()
                val contextAnnotations = classContextAnnotations + methodContextAnnotations
                current.methods.add(
                    ServiceMethod(
                        function = declaration,
                        ksMethodAnnotation = annotation,
                        metadataAnnotations = metadataAnnotations,
                        errorAnnotations = errorAnnotations,
                        contextAnnotations = contextAnnotations
                    )
                )
            } else if (!declaration.isFakeOverride) {
                if (declaration.dispatchReceiverParameter?.type?.classFqName !=
                    FqConstants.FQ_INTROSPECTABLE_RPC_SERVICE
                ) {
                    val receiverFqName =
                        declaration.dispatchReceiverParameter?.type?.classFqName
                    messageCollector.reportUserError(
                        "@KsMethod can only be applied to functions declared inside a " +
                            "@KsService interface. ${declaration.name.asString()} is " +
                            "declared on $receiverFqName.",
                        element = declaration
                    )
                }
            }
        } else {
            if (currentService != null && declaration.overriddenSymbols.isEmpty()) {
                messageCollector.reportUserWarning(
                    "${declaration.name.asString()} declared inside a @KsService " +
                        "without @KsMethod — the function will not be exposed over RPC.",
                    element = declaration
                )
            }
        }
        return super.visitSimpleFunction(declaration)
    }
}

private class SubclassVisitor(
    private val messageCollector: MessageCollector,
    private val classes: Map<String, ServiceClass>
) : IrElementTransformerVoid() {
    override fun visitClass(declaration: IrClass): IrStatement {
        val declFqName = declaration.fqNameForIrSerialization.asString()
        // Skip if this class is itself a @KsService (it has its own entry)
        if (declFqName in classes) {
            return super.visitClass(declaration)
        }
        val ksServiceSupers = declaration.getAllSuperclasses().mapNotNull {
            classes[it.fqNameForIrSerialization.asString()]
        }
        // Leaf-filter: keep only those not transitively extended by another in the list
        val leafSupers = ksServiceSupers.filter { candidate ->
            ksServiceSupers.none { other ->
                other != candidate &&
                    other.irClass.getAllSuperclasses().any {
                        it.fqNameForIrSerialization.asString() ==
                            candidate.irClass.fqNameForIrSerialization.asString()
                    }
            }
        }
        if (leafSupers.size > 1) {
            messageCollector.reportUserError(
                "${declaration.fqNameForIrSerialization.asString()} has " +
                    "multiple @KsService super types, which is not supported.",
                element = declaration
            )
        }
        leafSupers.singleOrNull()?.irClassAndImpls?.add(declaration)
        return super.visitClass(declaration)
    }
}

data class ServiceMethod(
    val function: IrSimpleFunction,
    val ksMethodAnnotation: IrConstructorCall,
    val metadataAnnotations: List<IrConstructorCall> = emptyList(),
    /**
     * `@KsError(code, type)` annotations captured in declaration order. The
     * compiler plugin emits one [com.monkopedia.ksrpc.KsErrorMapping] entry per
     * annotation into the generated `RpcMethod.errorMappings` list. See #77.
     */
    val errorAnnotations: List<IrConstructorCall> = emptyList(),
    /**
     * `@KsContext`-meta-annotated annotations captured from both the method
     * and its enclosing `@KsService` class. The compiler plugin emits one
     * [com.monkopedia.ksrpc.KsContextMapping] entry per unique binding into
     * the generated `RpcMethod.contextBindings` list. See #81.
     */
    val contextAnnotations: List<IrConstructorCall> = emptyList()
)

data class ServiceClass(
    val irClass: IrClass,
    val irClassAndImpls: MutableList<IrClass> = mutableListOf(irClass),
    val methods: MutableList<ServiceMethod> = mutableListOf()
) {
    val endpoints = mutableMapOf<String, IrFunction>()
    lateinit var channel: IrField
        private set
    lateinit var stubCompanion: IrClass
        private set
    lateinit var stubConstructor: IrConstructor
        private set

    /** Per-instance serializer fields on the Stub (one per class-level type parameter). */
    var stubSerializerFields: List<IrField> = emptyList()
        private set

    /** Generated nested `Obj<T, ...>` class for generic services, or null for non-generic. */
    var objClass: IrClass? = null
        private set

    /** Per-instance serializer fields on the Obj (one per class-level type parameter). */
    var objSerializerFields: List<IrField> = emptyList()
        private set

    /**
     * Anonymous `ServiceExecutor` classes for generic services, keyed by method endpoint.
     * Pre-created during [generateChildrenForClass] so that body-time IR (which runs during
     * iteration over Obj's children) doesn't have to mutate the class.
     */
    val genericExecutors: MutableMap<String, IrClass> = mutableMapOf()

    val isGeneric: Boolean get() = irClass.typeParameters.isNotEmpty()

    fun addEndpoint(endpoint: String, methodField: IrFunction) {
        endpoints[endpoint] = methodField
    }

    fun setChannel(it: IrField) {
        channel = it
    }

    fun setStubCompanion(it: IrClass) {
        stubCompanion = it
    }

    fun setStubConstructor(it: IrConstructor) {
        stubConstructor = it
    }

    fun setStubSerializerFields(fields: List<IrField>) {
        stubSerializerFields = fields
    }

    fun setObjClass(it: IrClass) {
        objClass = it
    }

    fun setObjSerializerFields(fields: List<IrField>) {
        objSerializerFields = fields
    }

    companion object {
        private val serviceAnnotationFqName =
            FqName("com.monkopedia.ksrpc.annotation.KsService")

        fun findServices(
            messageCollector: MessageCollector,
            moduleFragment: IrModuleFragment
        ): MutableMap<String, ServiceClass> {
            val visitor = Visitor(messageCollector)
            visitor.visitElement(moduleFragment)
            // Import inherited methods from parent @KsService classes
            importInheritedMethods(visitor.classes)
            SubclassVisitor(messageCollector, visitor.classes).visitElement(moduleFragment)
            return visitor.classes
        }

        /**
         * For each ServiceClass, walk its @KsService supertypes (found in [classes])
         * and import their ServiceMethod entries. Skip methods already present in the
         * child (by endpoint name extracted from the @KsMethod annotation value).
         */
        private fun importInheritedMethods(classes: MutableMap<String, ServiceClass>) {
            for ((_, serviceClass) in classes) {
                val existingEndpoints = serviceClass.methods.map { method ->
                    extractEndpointName(method)
                }.toMutableSet()
                // Walk supertypes to find parent @KsService classes
                val visited = mutableSetOf<String>()
                val queue = ArrayDeque<IrClass>()
                for (superType in serviceClass.irClass.superTypes) {
                    val superClass = superType.classOrNull?.owner ?: continue
                    val fqName = superClass.fqNameForIrSerialization.asString()
                    if (visited.add(fqName)) queue.add(superClass)
                }
                while (queue.isNotEmpty()) {
                    val current = queue.removeFirst()
                    val currentFqName = current.fqNameForIrSerialization.asString()
                    val parentService = classes[currentFqName]
                    if (parentService != null) {
                        for (method in parentService.methods) {
                            val endpoint = extractEndpointName(method)
                            if (endpoint !in existingEndpoints) {
                                existingEndpoints.add(endpoint)
                                serviceClass.methods.add(method)
                            }
                        }
                    }
                    // Continue walking up
                    for (superType in current.superTypes) {
                        val superClass = superType.classOrNull?.owner ?: continue
                        val fqName = superClass.fqNameForIrSerialization.asString()
                        if (visited.add(fqName)) queue.add(superClass)
                    }
                }
            }
        }

        /**
         * Extract the endpoint name from a ServiceMethod's @KsMethod annotation.
         * The first argument of @KsMethod is the endpoint string.
         */
        private fun extractEndpointName(method: ServiceMethod): String {
            val annotation = method.ksMethodAnnotation
            return annotation.arguments[0].constString()
                ?: method.function.name.asString()
        }
    }
}
