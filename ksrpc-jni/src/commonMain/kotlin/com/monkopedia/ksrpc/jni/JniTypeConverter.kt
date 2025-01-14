/**
 * Copyright (C) 2025 Jason Monk <monkopedia@gmail.com>
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
package com.monkopedia.ksrpc.jni

import kotlinx.serialization.KSerializer

interface JniTypeConverter<T> {
    val boolean: Converter<T, Boolean>

    val byte: Converter<T, Byte>

    val short: Converter<T, Short>

    val int: Converter<T, Int>

    val long: Converter<T, Long>

    val float: Converter<T, Float>

    val double: Converter<T, Double>

    val char: Converter<T, Char>

    val string: Converter<T, String>
}

interface Converter<T, N> {
    fun convertTo(rawValue: T?): N
    fun convertFrom(value: N): T?
}

expect fun <V> JniSer.converterOf(serializer: KSerializer<V>): Converter<*, V>
expect fun <T> newTypeConverter(): JniTypeConverter<T>
