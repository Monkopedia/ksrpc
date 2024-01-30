/**
 * Copyright (C) 2024 Jason Monk <monkopedia@gmail.com>
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

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.name.FqName

class KsrpcIrGenerationExtension(
    private val messageCollector: MessageCollector
) : IrGenerationExtension {

    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val classes = ServiceClass.findServices(messageCollector, moduleFragment)
        KsrpcGenerationEnvironment.create(pluginContext, messageCollector) { env ->
            for (cls in classes) {
                val clsType = cls.irClass.typeWith()
                messageCollector.report(
                    CompilerMessageSeverity.INFO,
                    "Generating for class ${
                    cls.irClass.name.identifier
                    } with ${cls.methods.size} methods"
                )
                if (env.validate(cls)) {
                    val (stubCls, methods) = StubGeneration.generate(cls, clsType, env)
                    CompanionGeneration.generate(cls, env, clsType, stubCls, methods)
                }
            }
        }
    }

    private fun KsrpcGenerationEnvironment.validate(cls: ServiceClass): Boolean {
        var isValid = true
        if (!validateClass(cls.irClass)) {
            isValid = false
        }
        val names = mutableSetOf<String>()
        for ((method, annotation) in cls.methods) {
            if (!validateMethod(cls.irClass, method, annotation, names)) {
                isValid = false
            }
        }
        return isValid
    }

    private val rpcServiceName = FqName("com.monkopedia.ksrpc.RpcService")

    private fun KsrpcGenerationEnvironment.validateClass(irClass: IrClass): Boolean {
        var isValid = true
        if (!irClass.superTypes.map { it.classFqName }.contains(rpcServiceName)) {
            messageCollector.report(
                CompilerMessageSeverity.ERROR,
                "${irClass.kotlinFqName.asString()} does not extend ${rpcServiceName.asString()}"
            )
            isValid = false
        }
        return isValid
    }

    private fun KsrpcGenerationEnvironment.validateMethod(
        irClass: IrClass,
        method: IrFunction,
        annotation: IrConstructorCall,
        names: MutableSet<String>
    ): Boolean {
        var isValid = true
        if (method.typeParameters.isNotEmpty()) {
            val fqName = irClass.kotlinFqName.asString()
            messageCollector.report(
                CompilerMessageSeverity.ERROR,
                "$fqName.${method.name.asString()} cannot have type parameters"
            )
            isValid = false
        }
        if (method.valueParameters.size > 1) {
            val fqName = irClass.kotlinFqName.asString()
            messageCollector.report(
                CompilerMessageSeverity.ERROR,
                "$fqName.${method.name.asString()} cannot have more than 1 parameter"
            )
            isValid = false
        }
        val name = (annotation.getValueArgument(0) as? IrConst<String>)?.value
        if (name == null) {
            val fqName = irClass.kotlinFqName.asString()
            val methodName = method.name.asString()
            val annotationArg = annotation.getValueArgument(0)
            messageCollector.report(
                CompilerMessageSeverity.ERROR,
                "$fqName.$methodName: could not parse annotation argument $annotationArg"
            )
            isValid = false
        } else if (!names.add(name)) {
            val fqName = irClass.kotlinFqName.asString()
            val methodName = method.name.asString()
            messageCollector.report(
                CompilerMessageSeverity.ERROR,
                "$fqName.$methodName: cannot use endpoint $name, it has already been used"
            )
            isValid = false
        }
        if (method.valueParameters[0].type.classFqName == byteReadChannel &&
            method.returnType.classFqName == byteReadChannel
        ) {
            val fqName = irClass.kotlinFqName.asString()
            val methodName = method.name.asString()
            messageCollector.report(
                CompilerMessageSeverity.ERROR,
                "$fqName.$methodName: ByteReadChannel not yet supported for both input and output"
            )
            isValid = false
        }
        return isValid
    }
}
