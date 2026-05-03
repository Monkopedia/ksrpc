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
import kotlin.experimental.ExperimentalNativeApi
import kotlin.reflect.AssociatedObjectKey
import kotlin.reflect.ExperimentalAssociatedObjects
import kotlin.reflect.KClass
import kotlin.reflect.findAssociatedObject

/**
 * Used to find [RpcObject] of services in native implementations.
 */
@OptIn(ExperimentalAssociatedObjects::class)
@AssociatedObjectKey
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
actual annotation class RpcObjectKey actual constructor(actual val rpcObject: KClass<*>)

@Suppress("UNCHECKED_CAST")
@OptIn(ExperimentalAssociatedObjects::class, KsrpcInternal::class)
actual inline fun <reified T : RpcService> rpcObject(): RpcObject<T> {
    val obj = T::class.findAssociatedObject<RpcObjectKey>()
    when (obj) {
        is RpcObject<*> -> return obj as RpcObject<T>

        is RpcObjectFactory<*> -> {
            val type = kotlin.reflect.typeOf<T>()
            val typeArgs = type.arguments.map {
                it.type ?: error(
                    "Star projection not supported in rpcObject<${T::class.simpleName}<...>>()"
                )
            }
            val factory = obj as RpcObjectFactory<T>
            // When typeArgs.size != factory.arity the most common cause is a plain-Kotlin
            // subtype of a generic @KsService — e.g. `interface XYZ : GenericEcho<String>`.
            // `findAssociatedObject` resolved the parent's factory (arity 1) but
            // `typeOf<XYZ>()` has no arguments, so the resolver can't supply the baked-in
            // type arg. This is issue #64 — the workaround today on Native/JS/WASM is to
            // call the parent factory directly, e.g. `GenericEcho.create(listOf(typeOf<String>()))`.
            if (typeArgs.size != factory.arity) {
                error(
                    "Can't resolve rpc companion for ${T::class.simpleName}: " +
                        "associated factory expects ${factory.arity} type argument(s) but " +
                        "typeOf<${T::class.simpleName}>() supplied ${typeArgs.size}. If " +
                        "${T::class.simpleName} is a plain Kotlin subtype of a generic " +
                        "@KsService, call the parent's factory directly with " +
                        "explicit type arguments (see issue #64)."
                )
            }
            return factory.getOrCreate(typeArgs)
        }
    }
    return error("Can't find rpc companion for ${T::class}")
}

@OptIn(ExperimentalNativeApi::class)
actual val Throwable.asString: String
    get() = this.message + "\n" + this.getStackTrace().joinToString("\n")
