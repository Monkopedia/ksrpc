/*
 * Copyright 2021 Jason Monk
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

import kotlin.test.fail
import kotlinx.coroutines.runBlocking
import kotlin.native.concurrent.withWorker

actual suspend inline fun httpTest(
    crossinline serve: suspend Routing.() -> Unit,
    test: suspend (Int) -> Unit
) {
    // Do nothing, disable HTTP hosting in Native tests.
}

actual suspend fun testServe(
    basePath: String,
    channel: SerializedService,
    errorListener: ErrorListener
): Routing.() -> Unit = {
    // Do nothing, disable HTTP hosting in Native tests.
}

actual fun Routing.testServeWebsocket(
    basePath: String,
    channel: SerializedService,
    errorListener: ErrorListener
) {
    // Do nothing, disable HTTP hosting in Native tests.
}

actual class Routing

internal actual fun runBlockingUnit(function: suspend () -> Unit) {
    try {
        runBlocking {
            function()
        }
    } catch (t: Throwable) {
        t.printStackTrace()
        fail("Caught exception $t")
    }
}
