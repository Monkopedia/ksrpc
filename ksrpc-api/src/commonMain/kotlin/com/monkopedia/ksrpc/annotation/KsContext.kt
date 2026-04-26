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

import com.monkopedia.ksrpc.KsContextBinding
import kotlin.reflect.KClass

/**
 * Meta-annotation that opts an annotation class into ksrpc's per-call
 * coroutine-context propagation. Apply this to an annotation class together
 * with a [binding] referencing a concrete [KsContextBinding] implementation
 * to declare that the annotated [KsMethod] (or every [KsMethod] inside an
 * annotated [KsService]) should propagate a [kotlin.coroutines.CoroutineContext.Element]
 * value across the wire.
 *
 * The [binding] supplies:
 *
 *  - A stable wire-level key ([KsContextBinding.wireKey]) used by transport
 *    layers to encode the value.
 *  - The [kotlin.coroutines.CoroutineContext.Key] identity itself —
 *    [KsContextBinding] extends [kotlin.coroutines.CoroutineContext.Key], so
 *    handlers find the propagated value via the standard
 *    `coroutineContext[SomeBinding]` lookup naming the binding directly.
 *  - String encode / decode functions ([KsContextBinding.toWire] /
 *    [KsContextBinding.fromWire]) used by transport layers that carry the
 *    context value as a textual representation.
 *
 * Compiler validation:
 *
 *  - The plugin rejects a `@KsContext`-meta-annotated annotation whose
 *    [binding] does not implement [KsContextBinding].
 *  - The plugin rejects a [KsMethod] (or its enclosing [KsService]) when two
 *    `@KsContext`-meta-annotated annotations apply whose bindings declare the
 *    same [KsContextBinding.wireKey].
 *
 * Code emission for stub-side put-into-context and handler-side
 * read-from-coroutine-context, plus per-transport wire formats, is handled by
 * follow-up work and is intentionally not part of this annotation's contract.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class KsContext(val binding: KClass<out KsContextBinding<*>>)
