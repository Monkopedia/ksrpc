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

/**
 * Opaque handle passed to a native host binding (the consumer's `external fun`
 * referenced by [KsrpcNativeHost.connect]). It bundles the per-connection JNI
 * context that the native side needs; the consumer never inspects it -- they
 * only name the type in their binding signature and forward the value to
 * `ksrpcHostConnection`. The members are read natively (by field access), so
 * the type deliberately exposes no public API.
 */
class JniHostInit internal constructor(
    internal val connection: JniConnection,
    internal val scope: Long,
    internal val output: JavaJniContinuation<Long>
)
