/**
 * Copyright (C) 2022 Jason Monk <monkopedia@gmail.com>
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

import com.monkopedia.ksrpc.channels.CallData
import com.monkopedia.ksrpc.channels.CallData.Companion
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.serialization.KSerializer
import kotlinx.serialization.StringFormat
import kotlinx.serialization.json.Json

/**
 * Global configuration for KSRPC channels and services.
 */
interface KsrpcEnvironment<T> {
    val serialization: CallDataSerializer<T>
    val defaultScope: CoroutineScope
    val errorListener: ErrorListener
    val coroutineExceptionHandler: CoroutineExceptionHandler

    interface Element<T> {
        val env: KsrpcEnvironment<T>
    }
}

interface CallDataSerializer<T> {
    fun <I> createCallData(serializer: KSerializer<I>, input: I): CallData<T>
    fun <I> createErrorCallData(serializer: KSerializer<I>, input: I): CallData<T>
    fun <I> decodeCallData(serializer: KSerializer<I>, data: CallData<T>): I
}

/**
 * Creates a copy of the [KsrpcEnvironment] provided and allows changes to it before returning
 * it. This method does NOT modify the original [KsrpcEnvironment].
 */
fun <T> KsrpcEnvironment<T>.reconfigure(builder: KsrpcEnvironmentBuilder<T>.() -> Unit): KsrpcEnvironment<T> {
    val b = (this as? KsrpcEnvironmentBuilder)?.copy()
        ?: KsrpcEnvironmentBuilder(serialization, defaultScope, errorListener)
    b.builder()
    return b
}

/**
 * Convenience method for easily creating a copy of [KsrpcEnvironment] with a local error listener.
 */
fun <T> KsrpcEnvironment<T>.onError(listener: ErrorListener): KsrpcEnvironment<T> {
    val b = (this as? KsrpcEnvironmentBuilder)?.copy()
        ?: KsrpcEnvironmentBuilder(serialization, defaultScope, errorListener)
    b.errorListener = listener
    return b
}

fun <T> ksrpcEnvironment(
    serializer: CallDataSerializer<T>,
    builder: KsrpcEnvironmentBuilder<T>.() -> Unit
): KsrpcEnvironment<T> {
    return KsrpcEnvironmentBuilder<T>(serializer).also(builder)
}

fun ksrpcEnvironment(
    stringFormat: StringFormat = Json,
    builder: KsrpcEnvironmentBuilder<String>.() -> Unit
): KsrpcEnvironment<String> {
    return ksrpcEnvironment(StringSerializer(stringFormat), builder)
}

private class StringSerializer(val stringFormat: StringFormat = Json) : CallDataSerializer<String> {
    override fun <I> createCallData(serializer: KSerializer<I>, input: I): CallData<String> {
        return CallData.create(stringFormat.encodeToString(serializer, input))
    }

    override fun <I> createErrorCallData(serializer: KSerializer<I>, input: I): CallData<String> {
        return CallData.create(ERROR_PREFIX + stringFormat.encodeToString(serializer, input))
    }

    override fun <I> decodeCallData(serializer: KSerializer<I>, data: CallData<String>): I {
        return stringFormat.decodeFromString(serializer, data.readSerialized())
    }
}

data class KsrpcEnvironmentBuilder<T> internal constructor(
    override var serialization: CallDataSerializer<T>,
    override var defaultScope: CoroutineScope = GlobalScope,
    override var errorListener: ErrorListener = ErrorListener { }
) : KsrpcEnvironment<T> {
    override val coroutineExceptionHandler: CoroutineExceptionHandler by lazy {
        CoroutineExceptionHandler { _, throwable ->
            errorListener.onError(throwable)
        }
    }
}
