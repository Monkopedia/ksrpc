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

import kotlin.reflect.KClass

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
data class MethodMetadata(
    val annotationFqName: String,
    val arguments: List<Pair<String, MetadataValue>>
) {
    /**
     * Look up the value of the argument with [name], or null if no such
     * argument was captured.
     */
    fun argument(name: String): MetadataValue? = arguments.firstOrNull { it.first == name }?.second

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
    data class StringValue(val value: String) : MetadataValue()

    data class IntValue(val value: Int) : MetadataValue()

    data class LongValue(val value: Long) : MetadataValue()

    data class BooleanValue(val value: Boolean) : MetadataValue()

    data class DoubleValue(val value: Double) : MetadataValue()

    data class FloatValue(val value: Float) : MetadataValue()

    /**
     * A `KClass<*>` literal captured directly as a real reference. The
     * compiler plugin emits the same `IrClassReference` already present on
     * the source-level annotation argument, so consumers receive the actual
     * `KClass` and can call reflection facilities directly when supported.
     */
    data class KClassValue(val kClass: KClass<*>) : MetadataValue() {
        override fun toString(): String = "${kClass.simpleName ?: "<anonymous>"}::class"
    }

    /**
     * An enum constant captured as a real reference to the enum entry. The
     * compiler plugin emits the same `IrGetEnumValue` already present on the
     * source-level annotation argument, so consumers receive the actual
     * enum constant.
     */
    data class EnumValue(val value: Enum<*>) : MetadataValue() {
        override fun toString(): String {
            val cls = value::class.simpleName ?: "<anonymous>"
            return "$cls.${value.name}"
        }
    }

    /**
     * A list/array argument. Annotation `vararg` and `Array<X>` arguments are
     * captured here.
     */
    data class ListValue(val items: List<MetadataValue>) : MetadataValue() {
        override fun toString(): String = items.joinToString(prefix = "[", postfix = "]")
    }
}
