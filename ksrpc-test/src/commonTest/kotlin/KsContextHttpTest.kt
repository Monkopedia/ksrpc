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

import kotlin.test.assertEquals
import kotlinx.coroutines.withContext

/**
 * Round-trip HTTP transport test for `@KsContext` propagation: the client sets context
 * entries, the HTTP transport carries them as headers, and the server decodes them
 * back into coroutine-context elements for the handler.
 */
class KsContextHttpTest :
    RpcFunctionalityTest(
        supportedTypes = listOf(TestType.HTTP),
        serializedChannel = {
            GreetHandler().serialized(ksrpcEnvironment { })
        },
        verifyOnChannel = { channel ->
            val stub = channel.toStub<ContextService, String>()
            withContext(AuthToken("http-token") + TraceId("http-trace")) {
                val result = stub.greet("hello")
                assertEquals("auth=http-token,trace=http-trace", result)
            }
        }
    )
