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
    val maxParallelReceives: Int

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
    override var serialization: StringFormat = Json,
    override var defaultScope: CoroutineScope = GlobalScope,
    override var errorListener: ErrorListener = ErrorListener { },
    override var maxParallelReceives: Int = 5
) : KsrpcEnvironment
