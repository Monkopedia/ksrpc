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
package com.monkopedia.ksrpc

import com.monkopedia.ksrpc.annotation.KsrpcInternal
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.reflect.KClass
import kotlin.reflect.full.allSupertypes
import kotlin.reflect.full.companionObjectInstance

/**
 * Resolve the [RpcObject] for [T].
 *
 * Resolution order:
 * 1. If `T::class` has a companion that is an [RpcObject], return it directly.
 * 2. If `T::class` has a companion that is an [RpcObjectFactory], use the type arguments
 *    from `typeOf<T>()` to build the concrete [RpcObject].
 * 3. Otherwise walk the supertypes of `T` (preserving type arguments) and return the first
 *    supertype whose classifier's companion is an [RpcObject] or an [RpcObjectFactory]. For
 *    a factory, the type arguments used are taken from THAT supertype's [kotlin.reflect.KType]
 *    (so e.g. `interface TypedStream : KsStream<Info>` correctly supplies `<Info>` to the
 *    parent's factory even though `TypedStream` itself has no type parameters).
 */
@Suppress("UNCHECKED_CAST")
@OptIn(KsrpcInternal::class)
actual inline fun <reified T : RpcService> rpcObject(): RpcObject<T> {
    val klass = T::class
    when (val companion = klass.companionObjectInstance) {
        is RpcObject<*> -> return companion as RpcObject<T>

        is RpcObjectFactory<*> -> {
            val type = kotlin.reflect.typeOf<T>()
            val typeArgs = type.arguments.map {
                it.type ?: error(
                    "Star projection not supported in rpcObject<${klass.simpleName}<...>>()"
                )
            }
            return (companion as RpcObjectFactory<T>).cachedCreate(typeArgs)
        }
    }
    // Walk supertypes via KClass.allSupertypes, which returns KTypes with type arguments
    // already substituted. For `interface TypedGenericEcho : GenericEcho<String>` this yields
    // a KType for `GenericEcho<String>` with `String` in `arguments` — exactly what the
    // factory needs.
    for (supertype in klass.allSupertypes) {
        val superKlass = supertype.classifier as? KClass<*> ?: continue
        when (val superCompanion = superKlass.companionObjectInstance) {
            is RpcObject<*> -> return superCompanion as RpcObject<T>

            is RpcObjectFactory<*> -> {
                val typeArgs = supertype.arguments.map {
                    it.type ?: error(
                        "Star projection not supported in supertype $supertype of $klass"
                    )
                }
                return (superCompanion as RpcObjectFactory<T>).cachedCreate(typeArgs)
            }
        }
    }
    return error("Can't find rpc companion for $klass")
}

actual val Throwable.asString: String
    get() = StringWriter().also {
        printStackTrace(PrintWriter(it))
    }.toString()
