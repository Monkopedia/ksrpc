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

import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred

class HttpTestPortRetryTest {

    @Test
    fun testHttpTestRetriesWhenPortIsInUse() = runBlockingUnit {
        val occupiedSocket = ServerSocket(0)
        val originalNextPort = nextPort.value
        try {
            val occupiedPort = occupiedSocket.localPort
            nextPort.value = occupiedPort
            val observedPort = CompletableDeferred<Int>()

            httpTest(
                serve = {},
                test = { port ->
                    observedPort.complete(port)
                },
                isWebsocket = false
            )

            assertTrue(
                observedPort.await() != occupiedPort,
                "httpTest should skip occupied port $occupiedPort"
            )
        } finally {
            occupiedSocket.close()
            if (nextPort.value < originalNextPort) {
                nextPort.value = originalNextPort
            }
        }
    }
}
