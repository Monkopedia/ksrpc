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
package com.monkopedia.ksrpc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer

class DataSchemaTests {
    @Test
    fun testString() = assertEquals(
        RpcDescriptor(RpcDescriptorType.STRING, "kotlin.String"),
        dataSchema(String.serializer())
    )

    @Test
    fun testBoolean() = assertEquals(
        RpcDescriptor(RpcDescriptorType.BOOLEAN, "kotlin.Boolean"),
        dataSchema(Boolean.serializer())
    )

    @Test
    fun testByte() = assertEquals(
        RpcDescriptor(RpcDescriptorType.BYTE, "kotlin.Byte"),
        dataSchema(Byte.serializer())
    )

    @Test
    fun testChar() = assertEquals(
        RpcDescriptor(RpcDescriptorType.CHAR, "kotlin.Char"),
        dataSchema(Char.serializer())
    )

    @Test
    fun testDouble() = assertEquals(
        RpcDescriptor(RpcDescriptorType.DOUBLE, "kotlin.Double"),
        dataSchema(Double.serializer())
    )

    @Test
    fun testFloat() = assertEquals(
        RpcDescriptor(RpcDescriptorType.FLOAT, "kotlin.Float"),
        dataSchema(Float.serializer())
    )

    @Test
    fun testInt() = assertEquals(
        RpcDescriptor(RpcDescriptorType.INT, "kotlin.Int"),
        dataSchema(Int.serializer())
    )

    @Test
    fun testLong() = assertEquals(
        RpcDescriptor(RpcDescriptorType.LONG, "kotlin.Long"),
        dataSchema(Long.serializer())
    )

    @Test
    fun testShort() = assertEquals(
        RpcDescriptor(RpcDescriptorType.SHORT, "kotlin.Short"),
        dataSchema(Short.serializer())
    )

    @Test
    fun testListListString() = assertEquals(
        RpcDescriptor(
            RpcDescriptorType.LIST,
            "kotlin.collections.ArrayList",
            mapOf(
                "0" to RpcDescriptor(
                    RpcDescriptorType.LIST,
                    "kotlin.collections.ArrayList",
                    mapOf(
                        "0" to RpcDescriptor(RpcDescriptorType.STRING, "kotlin.String")
                    )
                )
            )
        ),
        dataSchema(ListSerializer(ListSerializer(String.serializer())))
    )

    @Test
    fun testListString() = assertEquals(
        RpcDescriptor(
            RpcDescriptorType.LIST,
            "kotlin.collections.ArrayList",
            mapOf(
                "0" to RpcDescriptor(RpcDescriptorType.STRING, "kotlin.String")
            )
        ),
        dataSchema(ListSerializer(String.serializer()))
    )

    @Test
    fun testListBoolean() = assertEquals(
        RpcDescriptor(
            RpcDescriptorType.LIST,
            "kotlin.collections.ArrayList",
            mapOf(
                "0" to RpcDescriptor(RpcDescriptorType.BOOLEAN, "kotlin.Boolean")
            )
        ),
        dataSchema(ListSerializer(Boolean.serializer()))
    )

    @Test
    fun testListByte() = assertEquals(
        RpcDescriptor(
            RpcDescriptorType.LIST,
            "kotlin.collections.ArrayList",
            mapOf(
                "0" to RpcDescriptor(RpcDescriptorType.BYTE, "kotlin.Byte")
            )
        ),
        dataSchema(ListSerializer(Byte.serializer()))
    )

    @Test
    fun testListChar() = assertEquals(
        RpcDescriptor(
            RpcDescriptorType.LIST,
            "kotlin.collections.ArrayList",
            mapOf(
                "0" to RpcDescriptor(RpcDescriptorType.CHAR, "kotlin.Char")
            )
        ),
        dataSchema(ListSerializer(Char.serializer()))
    )

    @Test
    fun testListDouble() = assertEquals(
        RpcDescriptor(
            RpcDescriptorType.LIST,
            "kotlin.collections.ArrayList",
            mapOf(
                "0" to RpcDescriptor(RpcDescriptorType.DOUBLE, "kotlin.Double")
            )
        ),
        dataSchema(ListSerializer(Double.serializer()))
    )

    @Test
    fun testListFloat() = assertEquals(
        RpcDescriptor(
            RpcDescriptorType.LIST,
            "kotlin.collections.ArrayList",
            mapOf(
                "0" to RpcDescriptor(RpcDescriptorType.FLOAT, "kotlin.Float")
            )
        ),
        dataSchema(ListSerializer(Float.serializer()))
    )

    @Test
    fun testListInt() = assertEquals(
        RpcDescriptor(
            RpcDescriptorType.LIST,
            "kotlin.collections.ArrayList",
            mapOf(
                "0" to RpcDescriptor(RpcDescriptorType.INT, "kotlin.Int")
            )
        ),
        dataSchema(ListSerializer(Int.serializer()))
    )

    @Test
    fun testListLong() = assertEquals(
        RpcDescriptor(
            RpcDescriptorType.LIST,
            "kotlin.collections.ArrayList",
            mapOf(
                "0" to RpcDescriptor(RpcDescriptorType.LONG, "kotlin.Long")
            )
        ),
        dataSchema(ListSerializer(Long.serializer()))
    )

    @Test
    fun testListShort() = assertEquals(
        RpcDescriptor(
            RpcDescriptorType.LIST,
            "kotlin.collections.ArrayList",
            mapOf(
                "0" to RpcDescriptor(RpcDescriptorType.SHORT, "kotlin.Short")
            )
        ),
        dataSchema(ListSerializer(Short.serializer()))
    )

    @Test
    fun testMapString() = assertEquals(
        RpcDescriptor(
            RpcDescriptorType.MAP,
            "kotlin.collections.LinkedHashMap",
            mapOf(
                "0" to RpcDescriptor(RpcDescriptorType.STRING, "kotlin.String", id = 1),
                "1" to RpcDescriptor(RpcDescriptorType.STRING, "kotlin.String", id = 1)
            )
        ),
        dataSchema(MapSerializer(String.serializer(), String.serializer()))
    )

    @Test
    fun testMapBoolean() = assertEquals(
        RpcDescriptor(
            RpcDescriptorType.MAP,
            "kotlin.collections.LinkedHashMap",
            mapOf(
                "0" to RpcDescriptor(RpcDescriptorType.STRING, "kotlin.String"),
                "1" to RpcDescriptor(RpcDescriptorType.BOOLEAN, "kotlin.Boolean")
            )
        ),
        dataSchema(MapSerializer(String.serializer(), Boolean.serializer()))
    )

    @Test
    fun testMapByte() = assertEquals(
        RpcDescriptor(
            RpcDescriptorType.MAP,
            "kotlin.collections.LinkedHashMap",
            mapOf(
                "0" to RpcDescriptor(RpcDescriptorType.STRING, "kotlin.String"),
                "1" to RpcDescriptor(RpcDescriptorType.BYTE, "kotlin.Byte")
            )
        ),
        dataSchema(MapSerializer(String.serializer(), Byte.serializer()))
    )

    @Test
    fun testMapChar() = assertEquals(
        RpcDescriptor(
            RpcDescriptorType.MAP,
            "kotlin.collections.LinkedHashMap",
            mapOf(
                "0" to RpcDescriptor(RpcDescriptorType.STRING, "kotlin.String"),
                "1" to RpcDescriptor(RpcDescriptorType.CHAR, "kotlin.Char")
            )
        ),
        dataSchema(MapSerializer(String.serializer(), Char.serializer()))
    )

    @Test
    fun testMapDouble() = assertEquals(
        RpcDescriptor(
            RpcDescriptorType.MAP,
            "kotlin.collections.LinkedHashMap",
            mapOf(
                "0" to RpcDescriptor(RpcDescriptorType.STRING, "kotlin.String"),
                "1" to RpcDescriptor(RpcDescriptorType.DOUBLE, "kotlin.Double")
            )
        ),
        dataSchema(MapSerializer(String.serializer(), Double.serializer()))
    )

    @Test
    fun testMapFloat() = assertEquals(
        RpcDescriptor(
            RpcDescriptorType.MAP,
            "kotlin.collections.LinkedHashMap",
            mapOf(
                "0" to RpcDescriptor(RpcDescriptorType.STRING, "kotlin.String"),
                "1" to RpcDescriptor(RpcDescriptorType.FLOAT, "kotlin.Float")
            )
        ),
        dataSchema(MapSerializer(String.serializer(), Float.serializer()))
    )

    @Test
    fun testMapInt() = assertEquals(
        RpcDescriptor(
            RpcDescriptorType.MAP,
            "kotlin.collections.LinkedHashMap",
            mapOf(
                "0" to RpcDescriptor(RpcDescriptorType.STRING, "kotlin.String"),
                "1" to RpcDescriptor(RpcDescriptorType.INT, "kotlin.Int")
            )
        ),
        dataSchema(MapSerializer(String.serializer(), Int.serializer()))
    )

    @Test
    fun testMapLong() = assertEquals(
        RpcDescriptor(
            RpcDescriptorType.MAP,
            "kotlin.collections.LinkedHashMap",
            mapOf(
                "0" to RpcDescriptor(RpcDescriptorType.STRING, "kotlin.String"),
                "1" to RpcDescriptor(RpcDescriptorType.LONG, "kotlin.Long")
            )
        ),
        dataSchema(MapSerializer(String.serializer(), Long.serializer()))
    )

    @Test
    fun testMapShort() = assertEquals(
        RpcDescriptor(
            RpcDescriptorType.MAP,
            "kotlin.collections.LinkedHashMap",
            mapOf(
                "0" to RpcDescriptor(RpcDescriptorType.STRING, "kotlin.String"),
                "1" to RpcDescriptor(RpcDescriptorType.SHORT, "kotlin.Short")
            )
        ),
        dataSchema(MapSerializer(String.serializer(), Short.serializer()))
    )

    @Test
    fun testMyJson() = assertEquals(
        RpcDescriptor(
            RpcDescriptorType.CLASS,
            "com.monkopedia.ksrpc.MyJson",
            mapOf(
                "str" to RpcDescriptor(RpcDescriptorType.STRING, "kotlin.String"),
                "int" to RpcDescriptor(RpcDescriptorType.INT, "kotlin.Int"),
                "nFloat" to RpcDescriptor(RpcDescriptorType.FLOAT, "kotlin.Float?")
            )
        ),
        dataSchema(MyJson.serializer())
    )

    @Serializable
    data class RecursiveType(val str: String, val self: RecursiveType?)

    @Test
    fun testRecursiveType() = assertEquals(
        RpcDescriptor(
            RpcDescriptorType.CLASS,
            "com.monkopedia.ksrpc.DataSchemaTests.RecursiveType?",
            id = 0,
            elements = mapOf(
                "str" to RpcDescriptor(RpcDescriptorType.STRING, "kotlin.String"),
                "self" to RpcDescriptor(
                    RpcDescriptorType.CLASS,
                    "com.monkopedia.ksrpc.DataSchemaTests.RecursiveType?",
                    id = 0
                )
            )
        ),
        dataSchema(RecursiveType.serializer().nullable)
    )
}
