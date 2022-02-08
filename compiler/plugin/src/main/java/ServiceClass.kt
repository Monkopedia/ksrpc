/*
 * Copyright 2021 Jason Monk
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
package com.monkopedia.ksrpc.plugin

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.FqName

private class Visitor(private val messageCollector: MessageCollector) : IrElementTransformerVoid() {
    val classes = mutableMapOf<String, ServiceClass>()
    private var currentService: ServiceClass? = null
    private val method = FqName("com.monkopedia.ksrpc.annotation.KsMethod")
    private val service = FqName("com.monkopedia.ksrpc.annotation.KsService")

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
            classes[declaration.name.asString()] = currentService
                ?: error("Internal error, lost service")
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
                current.methods.add(declaration to annotation)
            } else {
                messageCollector.report(
                    CompilerMessageSeverity.ERROR,
                    "${declaration.name.asString()
                    } declared as KsMethod but not inside a KsService"
                )
            }
        } else {
            if (currentService != null) {
                messageCollector.report(
                    CompilerMessageSeverity.WARNING,
                    "${declaration.name.asString()
                    } declared within KsService without KsMethod annotation"
                )
            }
        }
        return super.visitSimpleFunction(declaration)
    }
}

data class ServiceClass(
    val irClass: IrClass,
    val methods: MutableList<Pair<IrSimpleFunction, IrConstructorCall>> = mutableListOf()
) {
    companion object {
        fun findServices(
            messageCollector: MessageCollector,
            moduleFragment: IrModuleFragment
        ): Collection<ServiceClass> {
            val visitor = Visitor(messageCollector)
            visitor.visitElement(moduleFragment)
            return visitor.classes.values
        }
    }
}
