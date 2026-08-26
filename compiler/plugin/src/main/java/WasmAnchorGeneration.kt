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
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.builders.irAnnotation
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin.GeneratedByPlugin
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.SYNTHETIC_OFFSET
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid

/**
 * Fills in the anchor properties declared by [FirKsrpcWasmAnchorGenerator]: initializer
 * `<Service>.Stub::class`, and `@EagerInitialization` so the initializer actually runs at
 * module load. Without the annotation the property is initialized lazily, is therefore
 * never initialized, and the anchor silently does nothing.
 */
class WasmAnchorGeneration(
    private val context: IrPluginContext,
    private val env: KsrpcGenerationEnvironment
) : IrVisitorVoid() {

    override fun visitElement(element: IrElement) {
        element.acceptChildrenVoid(this)
    }

    override fun visitProperty(declaration: IrProperty) {
        val origin = declaration.origin
        val key = (origin as? GeneratedByPlugin)?.pluginKey as? FirKsrpcWasmAnchorGenerator.Key
        if (key == null) {
            visitElement(declaration)
            return
        }
        // `context` is shadowed inside the IR builder lambda below.
        val pluginContext = context
        val eagerInitialization = env.eagerInitialization ?: reportInternal(
            "EagerInitialization is missing while compiling for wasm-js — the wasm anchor " +
                "for ${key.type} would compile but not run"
        )
        val stub = pluginContext.referenceClass(
            key.classId.createNestedClassId(FirKsrpcStubGenerator.STUB)
        ) ?: reportInternal("no generated Stub for ${key.type} to anchor")

        declaration.backingField?.initializer = pluginContext.irFactory.createExpressionBody(
            SYNTHETIC_OFFSET,
            SYNTHETIC_OFFSET,
            createClassReference(pluginContext, stub.owner)
        )
        declaration.getter?.let { getter ->
            getter.body = pluginContext.irBuilder(getter).irSynthBody {
                +irReturn(createClassReference(pluginContext, stub.owner))
            }
        }
        val constructor = eagerInitialization.constructors.firstOrNull()
            ?: reportInternal("EagerInitialization has no constructor")
        declaration.annotations +=
            pluginContext.irBuilder(declaration.symbol).irAnnotation(constructor, emptyList())
    }
}
