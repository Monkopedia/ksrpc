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
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.expressions.IrClassReference
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.starProjectedType
import org.jetbrains.kotlin.ir.types.typeWith

/**
 * Builds IR that materializes the `@KsError(code, type)` annotations captured
 * on a `@KsMethod` function into `List<KsErrorMapping>` at runtime, for
 * inclusion in the generated `RpcMethod` descriptor.
 *
 * Each entry emits `KsErrorMapping(code, type::class, serializer<Type>())`
 * where the serializer is resolved via `kotlinx.serialization.serializer<T>()`.
 * The payload type must be `@Serializable` — that validation is enforced
 * separately in [KsrpcIrGenerationExtension.validate] and surfaces a user
 * diagnostic before we reach codegen.
 *
 * Safe to instantiate even when `env.errorMappingSupported` is false — the
 * caller still needs to check that flag to decide whether to pass the
 * resulting list into the `RpcMethod` constructor.
 */
class ErrorMappingIrBuilder(
    private val env: KsrpcGenerationEnvironment,
    private val builder: DeclarationIrBuilder,
    @Suppress("unused") private val report: MessageCollector
) {
    init {
        check(env.errorMappingSupported) {
            "ksrpc internal: ErrorMappingIrBuilder created without error-mapping " +
                "dependencies — caller should guard on env.errorMappingSupported"
        }
    }

    private val ksErrorMapping = env.ksErrorMapping!!

    fun buildErrorMappingList(errorAnnotations: List<IrConstructorCall>): IrExpression =
        builder.irBuildListOf(env, ksErrorMapping.starProjectedType, errorAnnotations.map { buildMapping(it) })

    private fun buildMapping(annotation: IrConstructorCall): IrExpression {
        // Named-arg resolution: @KsError(code: Int, type: KClass<*>). The FIR/IR
        // argument list is positional, so we fetch by index and verify shape.
        val codeExpr = annotation.arguments.getOrNull(0) as? IrConst
            ?: reportInternal(
                "@KsError is missing its `code` argument or it is not an Int constant"
            )
        val typeExpr = annotation.arguments.getOrNull(1) as? IrClassReference
            ?: reportInternal(
                "@KsError is missing its `type` argument or it is not a KClass literal"
            )
        val codeValue = codeExpr.value as? Int ?: reportInternal(
            "@KsError.code is not an Int constant (got ${codeExpr.value?.let { it::class }})"
        )
        val errorType = typeExpr.classType
        val serializerFn = env.serializerMethod ?: reportInternal(
            "can't resolve kotlinx.serialization.serializer<T>() — " +
                "kotlinx-serialization-core must be on the compile classpath"
        )
        val serializerCall = builder.irCall(serializerFn).apply {
            typeArguments[0] = errorType
            type = env.kSerializer.typeWith(errorType)
        }
        return builder.irConstructOf(
            ksErrorMapping,
            builder.irInt(codeValue),
            typeExpr,
            serializerCall
        )
    }

}
