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

import java.io.PrintWriter
import java.io.StringWriter
import kotlin.reflect.full.allSuperclasses
import kotlin.reflect.full.companionObjectInstance

@Suppress("UNCHECKED_CAST")
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
            return (companion as RpcObjectFactory<T>).create(typeArgs)
        }
    }
    klass.allSuperclasses.find {
        it.companionObjectInstance is RpcObject<*>
    }?.let {
        return it.companionObjectInstance as RpcObject<T>
    }
    return error("Can't find rpc companion for $klass")
}

actual val Throwable.asString: String
    get() = StringWriter().also {
        printStackTrace(PrintWriter(it))
    }.toString()
