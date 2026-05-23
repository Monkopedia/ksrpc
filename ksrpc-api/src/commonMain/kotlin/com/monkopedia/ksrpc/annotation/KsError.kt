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
 * Binds a `@Serializable` `Throwable` subclass to a wire-level integer
 * error code on a [KsMethod]-annotated function.
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
 * The handler throws an instance of the bound type directly (no wrapper
 * required); ksrpc encodes the throwable using its `KSerializer` and emits
 * the bound `code` on the wire. The client deserializes back into the bound
 * type and re-throws — callers `catch (e: MyError)` typed.
 *
 * The `code` on the binding is the single source of truth for the wire
 * code; there is no per-throw override mechanism. If you want a different
 * code for the same data, bind it differently.
 *
 * The bound `type` must:
 *   - Be `@Serializable` (validated by the ksrpc compiler plugin)
 *   - Extend `Throwable` (or any subclass — typically `RuntimeException`)
 *   - Declare only fields safe to serialize (no `cause`, no inherited
 *     `stackTrace`); `message` is typically computed from the serialized
 *     fields:
 *
 * ```
 * @Serializable
 * class InitError(val retry: Boolean, val reason: String) : RuntimeException() {
 *     override val message: String get() = "init failed: $reason"
 * }
 * ```
 *
 * The server stack trace is NOT propagated across the wire — clients see a
 * stack from local deserialization. Use `message` and the serialized fields
 * for diagnostic info.
 *
 * The plugin captures `KSerializer<type>` and exposes a bidirectional map
 * on the generated `RpcMethod` descriptor — forward (code → KSerializer)
 * for client-side deserialization, reverse (KClass → code + KSerializer)
 * for server-side resolution of a thrown `t::class`. Unlike sibling
 * annotations propagated via `@KsMethodMetadata`, this marker is consumed
 * by the compiler plugin directly because the binding requires a real
 * serializer reference and a dedicated lookup table, not an opaque
 * metadata bag.
 *
 * @sample com.monkopedia.ksrpc.samples.errorAnnotationUsage
 * @sample com.monkopedia.ksrpc.samples.throwingTypedErrors
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
@Repeatable
annotation class KsError(val code: Int, val type: KClass<*>)
