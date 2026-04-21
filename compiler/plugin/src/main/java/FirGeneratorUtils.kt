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

import com.monkopedia.ksrpc.plugin.FqConstants.KSERIALIZER
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirTypeParameterRef
import org.jetbrains.kotlin.fir.plugin.FunctionBuildingContext
import org.jetbrains.kotlin.fir.plugin.createConeType
import org.jetbrains.kotlin.fir.scopes.impl.toConeType
import org.jetbrains.kotlin.fir.symbols.impl.FirTypeParameterSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * Helpers shared across the FIR declaration generators. Patterns captured here appear in
 * two or more of [FirCompanionDeclarationGenerator], [FirKsrpcObjGenerator] and
 * [FirKsrpcStubGenerator].
 */

/**
 * Build the name used for the per-type-parameter `KSerializer<T>` field/parameter on
 * generated `Obj`, `Stub` and factory `invoke`. Single source of truth for the naming
 * scheme so FIR and IR sides agree.
 */
internal fun serializerParameterName(typeParameterName: Name): Name =
    Name.identifier(typeParameterName.asString() + "Serializer")

/**
 * Adds a `KSerializer<Tn>` value parameter for each service/stub/obj type parameter, named
 * consistently (see [serializerParameterName]). The type provider is positional and uses
 * the surrounding declaration's type parameter refs by index.
 */
internal fun FunctionBuildingContext<*>.addSerializerValueParameters(
    session: FirSession,
    typeParams: List<FirTypeParameterSymbol>
) {
    for ((idx, tp) in typeParams.withIndex()) {
        valueParameter(
            serializerParameterName(tp.name),
            typeProvider = { refs ->
                KSERIALIZER.createConeType(
                    session,
                    arrayOf(refs[idx].symbol.toConeType())
                )
            }
        )
    }
}

/**
 * Builds `ClassId<T1, T2, ...>` as a ConeKotlinType whose type arguments are the passed
 * type-parameter refs, in order. Used to construct service instantiations that mirror the
 * enclosing declaration's type parameters (e.g. `RpcObject<Service<T1, T2>>`).
 */
internal fun ClassId.createConeTypeForRefs(
    session: FirSession,
    refs: List<FirTypeParameterRef>
): ConeKotlinType = createConeType(
    session,
    refs.map { it.symbol.toConeType() }.toTypedArray()
)

internal val LIST_CLASS_ID =
    ClassId(FqName("kotlin.collections"), Name.identifier("List"))
