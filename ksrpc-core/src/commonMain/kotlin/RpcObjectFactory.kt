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

import com.monkopedia.ksrpc.annotation.KsrpcInternal
import kotlin.reflect.KType
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.serializer

/**
 * Supertype of the generated companion for generic `@KsService` interfaces. Lets callers
 * materialize an [RpcObject] for a concrete instantiation of the service when they only
 * have [KType]s (for example, reflective/introspection code or sub-service transformers).
 *
 * Non-generic service companions directly implement [RpcObject] and do not need this
 * factory indirection — they are already an `RpcObject<Service>`.
 *
 * The preferred, type-safe entry point remains the companion's
 * `operator fun <T, ...> invoke(serializer, ...): RpcObject<Service<T, ...>>`; this
 * factory exists for contexts where a `KSerializer` isn't statically available.
 */
interface RpcObjectFactory<T : RpcService> {
    /** Number of type parameters the service declares. */
    val arity: Int

    /**
     * Create an [RpcObject] for a concrete instantiation of the service, resolving the
     * needed serializers from [typeArgs] via [kotlinx.serialization.serializer].
     *
     * @param typeArgs [KType]s for each type parameter, in declaration order.
     * @throws IllegalArgumentException if [typeArgs].size does not match [arity], or if
     *   any type argument is not `@Serializable` / not resolvable by kotlinx.serialization.
     */
    fun create(typeArgs: List<KType>): RpcObject<T>
}

/**
 * Resolve a [KSerializer] for [type] via [kotlinx.serialization.serializer], converting
 * any [SerializationException] into an [IllegalArgumentException] that names the offending
 * service and type. Used by generated `RpcObjectFactory.create` bodies.
 */
@KsrpcInternal
public fun resolveSerializerOrThrow(type: KType, serviceName: String): KSerializer<Any?> = try {
    serializer(type)
} catch (e: SerializationException) {
    throw IllegalArgumentException(
        "Type argument $type used with $serviceName must be @Serializable",
        e
    )
} catch (e: IllegalArgumentException) {
    // kotlinx.serialization throws IllegalArgumentException when it cannot find a
    // serializer through reflection (e.g. on platforms without kotlin-reflect). Rewrap
    // with a message that identifies the offending service.
    throw IllegalArgumentException(
        "Type argument $type used with $serviceName must be @Serializable",
        e
    )
}
