/**
 * Copyright (C) 2025 Jason Monk <monkopedia@gmail.com>
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

import java.io.PrintWriter
import java.io.StringWriter
import kotlin.reflect.full.companionObjectInstance

actual inline fun <reified T : RpcService> rpcObject(): RpcObject<T> {
    return T::class.companionObjectInstance as RpcObject<T>
}

actual val Throwable.asString: String
    get() = StringWriter().also {
        printStackTrace(PrintWriter(it))
    }.toString()
