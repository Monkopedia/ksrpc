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

@PublishedApi
internal actual fun serviceNameFor(serviceClass: KClass<*>): String =
    serviceClass.qualifiedName?.stripKClassPrefix()
        ?: serviceClass.toString().stripKClassPrefix()

@PublishedApi
internal actual fun RpcObject<*>.serviceInterfaceName(): String = (
    this::class.qualifiedName?.stripKClassPrefix()
        ?: this::class.toString().stripKClassPrefix()
    )
    .removeSuffix(".Companion")
    .removeSuffix("\$Companion")

private fun String.stripKClassPrefix(): String = removePrefix("class ")
    .removePrefix("interface ")
    .removePrefix("object ")
