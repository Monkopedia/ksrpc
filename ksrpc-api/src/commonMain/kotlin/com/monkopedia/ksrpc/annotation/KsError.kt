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
package com.monkopedia.ksrpc.annotation

import kotlin.reflect.KClass

/**
 * Binds a thrown error payload to a wire-level integer error code on a
 * [KsMethod]-annotated function.
 *
 * Declared at the call site with one entry per bindable error type:
 *
 * ```
 * @KsMethod("/initialize")
 * @KsError(code = 100, type = InitError::class)
 * @KsError(code = 101, type = VersionError::class)
 * suspend fun initialize(params: InitParams): InitResult
 * ```
 *
 * [type] must be a `@Serializable` data class (validated by the ksrpc
 * compiler plugin in Part 2 of #13). The plugin captures
 * `KSerializer<type>` and exposes a bidirectional map on the generated
 * `RpcMethod` descriptor — forward (code → KSerializer) for client-side
 * deserialization, reverse (KClass → code + KSerializer) for server-side
 * resolution of a thrown `data::class`.
 *
 * Unlike sibling annotations propagated via `@KsMethodMetadata`, this
 * marker is consumed by the compiler plugin directly because the binding
 * requires a real serializer reference and a dedicated lookup table, not
 * an opaque metadata bag.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
@Repeatable
annotation class KsError(val code: Int, val type: KClass<*>)
