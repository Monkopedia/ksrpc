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
@file:OptIn(KsrpcInternal::class)

package com.monkopedia.ksrpc

import com.monkopedia.ksrpc.annotation.KsrpcInternal
import com.monkopedia.ksrpc.channels.CallData
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
    val logger: Logger
    val coroutineExceptionHandler: CoroutineExceptionHandler

    interface Element<T> {
        val env: KsrpcEnvironment<T>
    }
}

/**
 * Bridges the user's wire-format `T` (a JSON string, a JNI [Any?] graph, etc.)
 * to / from typed values. Implementations are tied to a specific wire format:
 * a `StringSerializer` round-trips through `kotlinx.serialization`'s string
 * formats, a `JniSerialization` round-trips through the JNI `Any?` envelope,
 * and so on.
 *
 * Only successful payloads pass through this interface. Error responses are
 * carried as the [CallData.Error] variant by the routing layer; transports
 * encode and decode that variant natively (HTTP status codes, JSON-RPC error
 * envelopes, packet error fields, etc.) and never round-trip an error through
 * this serializer.
 */
interface CallDataSerializer<T> {
    fun <I> createCallData(serializer: KSerializer<I>, input: I): CallData<T>
    fun <I> decodeCallData(serializer: KSerializer<I>, data: CallData<T>): I
}

/**
 * Creates a copy of the [KsrpcEnvironment] provided and allows changes to it before returning
 * it. This method does NOT modify the original [KsrpcEnvironment].
 */
fun <T> KsrpcEnvironment<T>.reconfigure(
    builder: KsrpcEnvironmentBuilder<T>.() -> Unit
): KsrpcEnvironment<T> {
    val b = KsrpcEnvironmentBuilder(serialization, defaultScope, logger, errorListener)
    b.builder()
    return b
}

/**
 * Convenience method for easily creating a copy of [KsrpcEnvironment] with a local error listener.
 */
fun <T> KsrpcEnvironment<T>.onError(listener: ErrorListener): KsrpcEnvironment<T> {
    val b = KsrpcEnvironmentBuilder(serialization, defaultScope, logger, errorListener)
    b.errorListener = listener
    return b
}

fun <T> ksrpcEnvironment(
    serializer: CallDataSerializer<T>,
    builder: KsrpcEnvironmentBuilder<T>.() -> Unit
): KsrpcEnvironment<T> = KsrpcEnvironmentBuilder<T>(serializer).also(builder)

/**
 * Creates a string-based [KsrpcEnvironment] using the given [stringFormat] (JSON by default).
 *
 * The [builder] lambda configures optional settings such as the
 * [KsrpcEnvironmentBuilder.errorListener]. Pass a custom [kotlinx.serialization.StringFormat]
 * to control how payloads are encoded on the wire.
 *
 * @sample com.monkopedia.ksrpc.samples.environmentBasicSetup
 * @sample com.monkopedia.ksrpc.samples.environmentWithErrorListener
 */
fun ksrpcEnvironment(
    stringFormat: StringFormat = Json,
    builder: KsrpcEnvironmentBuilder<String>.() -> Unit
): KsrpcEnvironment<String> = ksrpcEnvironment(StringSerializer(stringFormat), builder)

private class StringSerializer(val stringFormat: StringFormat = Json) : CallDataSerializer<String> {
    override fun <I> createCallData(serializer: KSerializer<I>, input: I): CallData<String> =
        CallData.create(stringFormat.encodeToString(serializer, input))

    override fun <I> decodeCallData(serializer: KSerializer<I>, data: CallData<String>): I =
        stringFormat.decodeFromString(serializer, data.readSerialized())
}

class KsrpcEnvironmentBuilder<T> internal constructor(
    override var serialization: CallDataSerializer<T>,
    override var defaultScope: CoroutineScope = GlobalScope,
    override var logger: Logger = object : Logger {},
    override var errorListener: ErrorListener = ErrorListener { }
) : KsrpcEnvironment<T> {
    override val coroutineExceptionHandler: CoroutineExceptionHandler by lazy {
        CoroutineExceptionHandler { _, throwable ->
            errorListener.onError(throwable)
        }
    }
}
