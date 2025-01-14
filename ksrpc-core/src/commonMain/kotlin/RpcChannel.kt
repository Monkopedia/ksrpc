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

/**
 * Interface used for handling any errors that occur during hosting.
 */
fun interface ErrorListener {
    /**
     * Called when an error has occured during a hosted (incoming) call.
     *
     * The error will also be passed back to the client, this is purely for
     * monitoring purposes.
     */
    fun onError(t: Throwable)
}

expect val Throwable.asString: String

const val ERROR_PREFIX = "ERROR:"
