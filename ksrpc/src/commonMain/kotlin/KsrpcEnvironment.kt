package com.monkopedia.ksrpc

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.serialization.StringFormat
import kotlinx.serialization.json.Json

/**
 * Global configuration for KSRPC channels and services.
 */
interface KsrpcEnvironment {
    val serialization: StringFormat
    val defaultScope: CoroutineScope
    val errorListener: ErrorListener

    interface Element {
        val env: KsrpcEnvironment
    }
}

/**
 * Creates a copy of the [KsrpcEnvironment] provided and allows changes to it before returning
 * it. This method does NOT modify the original [KsrpcEnvironment].
 */
fun KsrpcEnvironment.reconfigure(builder: KsrpcEnvironmentBuilder.() -> Unit): KsrpcEnvironment {
    val b = (this as? KsrpcEnvironmentBuilder)?.copy()
        ?: KsrpcEnvironmentBuilder(serialization, defaultScope, errorListener)
    b.builder()
    return b
}

/**
 * Convenience method for easily creating a copy of [KsrpcEnvironment] with a local error listener.
 */
fun KsrpcEnvironment.onError(listener: ErrorListener): KsrpcEnvironment {
    val b = (this as? KsrpcEnvironmentBuilder)?.copy()
        ?: KsrpcEnvironmentBuilder(serialization, defaultScope, errorListener)
    b.errorListener = listener
    return b
}

fun ksrpcEnvironment(builder: KsrpcEnvironmentBuilder.() -> Unit): KsrpcEnvironment {
    return KsrpcEnvironmentBuilder().also(builder)
}

data class KsrpcEnvironmentBuilder internal constructor(
    override var serialization: StringFormat = Json { },
    override var defaultScope: CoroutineScope = GlobalScope,
    override var errorListener: ErrorListener = ErrorListener { }
) : KsrpcEnvironment
