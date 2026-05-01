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
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irVararg
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.expressions.IrClassReference
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.starProjectedType
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.kotlinFqName

/**
 * Builds IR that materializes the `@KsContext(binding = ...)` meta-annotations
 * captured on a `@KsMethod` function (and its enclosing `@KsService` interface)
 * into `List<KsContextMapping>` at runtime, for inclusion in the generated
 * `RpcMethod` descriptor.
 *
 * Each entry emits `KsContextMapping(BindingObject)` where the binding is a
 * singleton (companion object / named object) resolved via `irGetObject`.
 *
 * Pattern follows [ErrorMappingIrBuilder].
 */
class ContextBindingIrBuilder(
    private val env: KsrpcGenerationEnvironment,
    private val builder: DeclarationIrBuilder,
    @Suppress("unused") private val report: MessageCollector
) {
    init {
        check(env.contextBindingSupported) {
            "ksrpc internal: ContextBindingIrBuilder created without context-binding " +
                "dependencies — caller should guard on env.contextBindingSupported"
        }
    }

    private val ksContextMapping = env.ksContextMapping!!

    /**
     * Build a `listOf(KsContextMapping(...), ...)` IR expression from the
     * collected `@KsContext`-meta-annotated annotations. Each annotation's
     * `binding` argument (a KClass literal referencing a singleton) is resolved
     * to an `irGetObject` call.
     */
    fun buildContextBindingList(
        contextAnnotations: List<IrConstructorCall>
    ): IrExpression {
        // Deduplicate by binding class symbol — the same binding can appear from
        // both class-level and method-level annotations.
        val seen = mutableSetOf<IrClassSymbol>()
        val uniqueMappings = contextAnnotations.mapNotNull { annotation ->
            val bindingSymbol = extractBindingSymbol(annotation) ?: return@mapNotNull null
            if (!seen.add(bindingSymbol)) return@mapNotNull null
            buildMapping(bindingSymbol)
        }
        return buildListOf(
            ksContextMapping.starProjectedType,
            uniqueMappings
        )
    }

    /**
     * Extract the binding class symbol from a `@KsContext(binding = X::class)`
     * meta-annotation. The meta-annotation is found on the user-facing annotation
     * class (e.g. `@WithTrace`), and its `binding` argument is the first
     * (positional) argument of the `@KsContext` annotation.
     */
    private fun extractBindingSymbol(
        userAnnotation: IrConstructorCall
    ): IrClassSymbol? {
        // userAnnotation is e.g. @WithTrace — find @KsContext on its annotation class
        val annotationClass = userAnnotation.type.classOrNull?.owner ?: return null
        val ksContext = annotationClass.annotations.firstOrNull { metaAnn ->
            metaAnn.type.classOrNull?.owner?.kotlinFqName
                ?.asString() == "com.monkopedia.ksrpc.annotation.KsContext"
        } ?: return null
        val bindingRef = ksContext.arguments.getOrNull(0) as? IrClassReference ?: return null
        return bindingRef.classType.classOrNull
    }

    private fun buildMapping(bindingSymbol: IrClassSymbol): IrExpression {
        return builder.irCallConstructor(
            ksContextMapping.constructors.first(),
            emptyList()
        ).apply {
            type = ksContextMapping.starProjectedType
            putArgs(builder.irGetObject(bindingSymbol))
        }
    }

    private fun buildListOf(
        elementType: org.jetbrains.kotlin.ir.types.IrType,
        items: List<IrExpression>
    ): IrExpression = builder.irCall(env.listOfFunction).apply {
        typeArguments[0] = elementType
        val varargParameter = env.listOfFunction.owner.parameters
            .single { it.kind == IrParameterKind.Regular }
        arguments[varargParameter] = builder.irVararg(elementType, items)
    }
}
