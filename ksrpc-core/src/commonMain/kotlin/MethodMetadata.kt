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

/**
 * A captured sibling annotation on a `@KsMethod` function.
 *
 * The ksrpc compiler plugin walks every annotation on a `@KsMethod` function
 * whose annotation class is itself annotated with
 * `com.monkopedia.ksrpc.annotation.KsMethodMetadata`, decodes its arguments
 * into [MetadataValue]s, and attaches a [MethodMetadata] entry to the generated
 * [RpcMethod]. Transport layers can then look the metadata up by
 * [annotationFqName] when serializing a call.
 *
 * The container is intentionally generic — older ksrpc versions reading a
 * descriptor produced by a newer version see unfamiliar metadata entries as
 * opaque [MethodMetadata] objects rather than failing. Consumers that don't
 * know the annotation can ignore it; consumers that do know it can decode the
 * arguments by name.
 *
 * @property annotationFqName the fully qualified name of the captured
 *   annotation class, e.g. `com.example.MyMarker`.
 * @property arguments the captured annotation arguments, in declaration order,
 *   each as a name → [MetadataValue] pair. Default-valued arguments that the
 *   call site does not specify are not represented.
 */
class MethodMetadata(
    val annotationFqName: String,
    val arguments: List<Pair<String, MetadataValue>>
) {
    /**
     * Look up the value of the argument with [name], or null if no such
     * argument was captured.
     */
    fun argument(name: String): MetadataValue? = arguments.firstOrNull { it.first == name }?.second

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MethodMetadata) return false
        return annotationFqName == other.annotationFqName && arguments == other.arguments
    }

    override fun hashCode(): Int = 31 * annotationFqName.hashCode() + arguments.hashCode()

    override fun toString(): String {
        val args = arguments.joinToString { "${it.first}=${it.second}" }
        return "MethodMetadata($annotationFqName, $args)"
    }
}

/**
 * A typed wrapper for an argument captured into a [MethodMetadata].
 *
 * The set of permitted shapes is fixed at this sealed hierarchy so the
 * compiler plugin and consumers agree on what values can travel in a method
 * descriptor. Arbitrary `Any?` is intentionally not permitted: every captured
 * value has a concrete static type.
 */
sealed class MetadataValue {
    class StringValue(val value: String) : MetadataValue() {
        override fun equals(other: Any?): Boolean =
            this === other || (other is StringValue && value == other.value)
        override fun hashCode(): Int = value.hashCode()
        override fun toString(): String = "\"$value\""
    }

    class IntValue(val value: Int) : MetadataValue() {
        override fun equals(other: Any?): Boolean =
            this === other || (other is IntValue && value == other.value)
        override fun hashCode(): Int = value.hashCode()
        override fun toString(): String = value.toString()
    }

    class LongValue(val value: Long) : MetadataValue() {
        override fun equals(other: Any?): Boolean =
            this === other || (other is LongValue && value == other.value)
        override fun hashCode(): Int = value.hashCode()
        override fun toString(): String = "${value}L"
    }

    class BooleanValue(val value: Boolean) : MetadataValue() {
        override fun equals(other: Any?): Boolean =
            this === other || (other is BooleanValue && value == other.value)
        override fun hashCode(): Int = value.hashCode()
        override fun toString(): String = value.toString()
    }

    class DoubleValue(val value: Double) : MetadataValue() {
        override fun equals(other: Any?): Boolean =
            this === other || (other is DoubleValue && value == other.value)
        override fun hashCode(): Int = value.hashCode()
        override fun toString(): String = value.toString()
    }

    class FloatValue(val value: Float) : MetadataValue() {
        override fun equals(other: Any?): Boolean =
            this === other || (other is FloatValue && value == other.value)
        override fun hashCode(): Int = value.hashCode()
        override fun toString(): String = "${value}f"
    }

    /**
     * A `KClass<*>` literal captured by FQ name. The class itself is not
     * dereferenced at descriptor-build time — the FQ name is the only field.
     * Consumers that need to resolve it to a live class do so via their own
     * reflection facilities on platforms that support it.
     */
    class KClassValue(val classFqName: String) : MetadataValue() {
        override fun equals(other: Any?): Boolean =
            this === other || (other is KClassValue && classFqName == other.classFqName)
        override fun hashCode(): Int = classFqName.hashCode()
        override fun toString(): String = "$classFqName::class"
    }

    /**
     * An enum constant captured as the FQ name of the enum class plus the
     * constant's name.
     */
    class EnumValue(val enumFqName: String, val entryName: String) : MetadataValue() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is EnumValue) return false
            return enumFqName == other.enumFqName && entryName == other.entryName
        }
        override fun hashCode(): Int = 31 * enumFqName.hashCode() + entryName.hashCode()
        override fun toString(): String = "$enumFqName.$entryName"
    }

    /**
     * A list/array argument. Annotation `vararg` and `Array<X>` arguments are
     * captured here.
     */
    class ListValue(val items: List<MetadataValue>) : MetadataValue() {
        override fun equals(other: Any?): Boolean =
            this === other || (other is ListValue && items == other.items)
        override fun hashCode(): Int = items.hashCode()
        override fun toString(): String = items.joinToString(prefix = "[", postfix = "]")
    }
}
