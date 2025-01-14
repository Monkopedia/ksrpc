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

interface BasicList<T> {
    val size: Int

    operator fun get(index: Int): T

    val asSerialized: JniSerialized
}

interface MutableBasicList<T> : BasicList<T> {
    operator fun set(index: Int, value: T)
    fun add(value: T)
}

expect fun <T> newList(): MutableBasicList<T>
