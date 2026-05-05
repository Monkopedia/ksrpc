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

/**
 * Marker annotation applied by the ksrpc compiler plugin to synthetic
 * classes (`Stub`, `Companion`, `Obj`, etc.) that are generated for every
 * `@KsService` interface. These declarations are part of the public API
 * surface a consumer interacts with through their service interface, but
 * they are not authored by the consumer and their exact shape is an
 * implementation detail of the plugin.
 *
 * Add
 *
 * ```kotlin
 * apiValidation {
 *     nonPublicMarkers += "com.monkopedia.ksrpc.annotation.KsrpcGenerated"
 * }
 * ```
 *
 * to your binary-compatibility-validator configuration to exclude these
 * generated declarations from API dumps so plugin-internal changes do not
 * trigger spurious `apiCheck` failures on upgrade.
 */
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.ANNOTATION_CLASS,
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY
)
annotation class KsrpcGenerated
