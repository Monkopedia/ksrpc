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
 * Declares the type used to decode the `error.data` field when this RPC
 * method returns an error from the remote side.
 *
 * When present on a `@KsMethod` function, the generated stub will attempt to
 * deserialize the error payload into [errorType] using its kotlinx-serialization
 * serializer. The deserialized value is then attached to the thrown
 * [com.monkopedia.ksrpc.KsrpcException] so callers can inspect structured
 * error details.
 *
 * The [errorType] class **must** be annotated with `@Serializable`.
 *
 * If the annotation is absent, errors follow the default path (string message
 * only, no typed data).
 *
 * @property errorType the `@Serializable` class to decode error data into.
 */
@KsMethodMetadata
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class KsErrorData(val errorType: KClass<*>)
