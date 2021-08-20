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
package com.monkopedia.ksrpc

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import nanoid.NanoIdUtils
import kotlin.reflect.AssociatedObjectKey
import kotlin.reflect.ExperimentalAssociatedObjects
import kotlin.reflect.KClass
import kotlin.reflect.findAssociatedObject

actual fun randomUuid(): String {
    return NanoIdUtils.randomNanoId()
}
actual val DEFAULT_DISPATCHER: CoroutineDispatcher
    get() = Dispatchers.Default

actual val Throwable.asString: String
    get() = this.getStackTrace().joinToString("\n")

@OptIn(ExperimentalAssociatedObjects::class)
@AssociatedObjectKey
@Retention(AnnotationRetention.BINARY)
annotation class RpcObjectKey(val rpcObject: KClass<out RpcObject<*>>)

@OptIn(ExperimentalAssociatedObjects::class)
actual inline fun <reified T : RpcService> rpcObject(): RpcObject<T> {
    return T::class.findAssociatedObject<RpcObjectKey>() as RpcObject<T>
}
