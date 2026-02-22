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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class JniListWrapperTest {

    @Test
    fun toListUsesViewForDecodedSlices() {
        val backing = mutableListOf<Any?>(10, 11, 12, 13)
        val wrapper = JavaListWrapper(backing)
        val sliced = SlicedBasicList(wrapper, offset = 1, size = 2)
        val projected = toList(JniSerialized(sliced))
        assertIs<JavaListWrapper<*>>(sliced.source)
        val sentinel = Any()
        backing[sliced.offset] = sentinel

        assertSame(sentinel, projected[0])
    }

    @Test
    fun toListCopiesUnknownBasicListImplementations() {
        val values = listOf<Any?>(1, "two", null)
        val custom =
            object : BasicList<Any?> {
                override val size: Int = values.size
                override fun get(index: Int): Any? = values[index]
                override val asSerialized: JniSerialized = JniSerialized(this)
            }

        assertEquals(values, toList(custom.asSerialized))
    }
}
