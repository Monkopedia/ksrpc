/**
 * Copyright (C) 2022 Jason Monk <monkopedia@gmail.com>
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
 * Annotation tagging an interface for processing by the compiler plugin.
 *
 * interfaces tagged with this are expected to extend [RpcService], and will
 * have a companion generated for them that implements [RpcObject] for itself.
 */
annotation class KsService

/**
 * Tags a method within a [KsService] for rpc calls.
 *
 * The [name] must be unique within a [KsService] but need not be unique
 * globally.
 */
annotation class KsMethod(val name: String)
