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
package com.monkopedia.ksrpc.plugin

import com.monkopedia.ksrpc.plugin.FqConstants.BYTE_READ_CHANNEL
import com.monkopedia.ksrpc.plugin.FqConstants.FQRPC_SERVICE
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity.ERROR
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity.WARNING
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.backend.js.utils.isDispatchReceiver
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid

class KsrpcIrGenerationExtension(private val report: MessageCollector) : IrGenerationExtension {

    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val classes = ServiceClass.findServices(report, moduleFragment)
        classes.values.removeIf { !validate(it) }

        val env = KsrpcGenerationEnvironment(pluginContext, report)
        val transformers = listOf(
            StubGeneration(pluginContext, report, classes, env),
            CompanionGeneration(pluginContext, classes, env)
        )
        for (transformer in transformers) {
            moduleFragment.acceptChildrenVoid(transformer)
        }
    }

    private fun validate(cls: ServiceClass): Boolean {
        val names = mutableSetOf<String>()
        return cls.methods.fold(validateClass(cls.irClass)) { current, (method, annotation) ->
            validateMethod(cls.irClass, method, annotation, names) && current
        }
    }

    private fun validateClass(irClass: IrClass): Boolean {
        var isValid = true
        if (!irClass.superTypes.map { it.classFqName }.contains(FQRPC_SERVICE)) {
            report.error(
                "${irClass.kotlinFqName.asString()} does not extend ${FQRPC_SERVICE.asString()}"
            )
            isValid = false
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
            report.error("$fqName.${method.name.asString()} cannot have type parameters")
            isValid = false
        }
        if (method.parameters.filter { !it.isDispatchReceiver }.size > 1) {
            val fqName = irClass.kotlinFqName.asString()
            report.error("$fqName.${method.name.asString()} cannot have more than 1 parameter")
            isValid = false
        }
        val annotationArg = annotation.arguments[0]
        val name = annotationArg.constString()
        if (name == null) {
            val fqName = irClass.kotlinFqName.asString()
            val methodName = method.name.asString()
            report.error("$fqName.$methodName: could not parse annotation argument $annotationArg")
            isValid = false
        } else if (!names.add(name)) {
            val fqName = irClass.kotlinFqName.asString()
            val methodName = method.name.asString()
            report.error("$fqName.$methodName: cannot use endpoint $name, it has already been used")
            isValid = false
        }
        val inputType = method.parameters[0].type.classFqName
        val outputType = method.returnType.classFqName
        if (inputType == BYTE_READ_CHANNEL && outputType == BYTE_READ_CHANNEL) {
            val fqName = irClass.kotlinFqName.asString()
            val methodName = method.name.asString()
            report.error(
                "$fqName.$methodName: ByteReadChannel not yet supported for both input and output"
            )
            isValid = false
        }
        return isValid
    }
}

fun MessageCollector.error(msg: String) = report(ERROR, msg)
fun MessageCollector.warn(msg: String) = report(WARNING, msg)

fun IrExpression?.constString() = (this as? IrConst)?.value as? String
