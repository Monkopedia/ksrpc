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

import com.monkopedia.ksrpc.jsonrpc.internal.JsonRpcTransformer
import com.monkopedia.ksrpc.jsonrpc.internal.JsonRpcWriterBase
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.JsonElement

class JsonRpcWriterCloseTest {

    @Test
    fun testCloseIgnoresIllegalStateFromTransformerClose() = runBlockingUnit {
        val transformer =
            object : JsonRpcTransformer() {
                override val isOpen: Boolean = false

                override suspend fun send(message: JsonElement) {
                    error("unused")
                }

                override suspend fun receive(): JsonElement? = null

                override fun close(cause: Throwable?): Unit =
                    throw IllegalStateException("already closed")
            }
        val writer =
            JsonRpcWriterBase(
                scope = CoroutineScope(coroutineContext + SupervisorJob()),
                context = coroutineContext,
                env = ksrpcEnvironment { },
                comm = transformer
            )

        writer.close()
    }
}
