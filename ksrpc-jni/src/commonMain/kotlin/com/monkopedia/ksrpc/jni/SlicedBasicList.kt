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
package com.monkopedia.ksrpc.jni

internal class SlicedBasicList<T>(
    internal val source: BasicList<T>,
    internal val offset: Int,
    override val size: Int
) : BasicList<T> {

    init {
        require(offset >= 0) { "Slice offset cannot be negative: $offset" }
        require(size >= 0) { "Slice size cannot be negative: $size" }
        val endExclusive = offset + size
        require(endExclusive >= offset && endExclusive <= source.size) {
            "Slice [$offset, ${offset.toLong() + size.toLong()}) " +
                "is out of bounds for source size ${source.size}"
        }
    }

    override fun get(index: Int): T {
        if (index < 0 || index >= size) {
            throw IndexOutOfBoundsException("Index $index is out of bounds for size $size")
        }
        return source[offset + index]
    }

    override val asSerialized: JniSerialized
        get() = JniSerialized(this)
}
