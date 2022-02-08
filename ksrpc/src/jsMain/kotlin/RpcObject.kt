package com.monkopedia.ksrpc

import kotlin.reflect.AssociatedObjectKey
import kotlin.reflect.ExperimentalAssociatedObjects
import kotlin.reflect.KClass
import kotlin.reflect.findAssociatedObject


/**
 * Used to find [RpcObject] of services in js implementations.
 */
@OptIn(ExperimentalAssociatedObjects::class)
@AssociatedObjectKey
@Retention(AnnotationRetention.BINARY)
annotation class RpcObjectKey(val rpcObject: KClass<out RpcObject<*>>)

@OptIn(ExperimentalAssociatedObjects::class)
actual inline fun <reified T : RpcService> rpcObject(): RpcObject<T> {
    return T::class.findAssociatedObject<RpcObjectKey>() as RpcObject<T>
}

actual val Throwable.asString: String
    get() = toString()