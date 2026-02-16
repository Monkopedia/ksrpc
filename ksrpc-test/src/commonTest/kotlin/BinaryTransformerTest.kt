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

import com.monkopedia.ksrpc.channels.CallData
import com.monkopedia.ksrpc.channels.SerializedService
import io.ktor.utils.io.ByteChannel
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue

private class BinaryTransformerTestService(
    override val env: KsrpcEnvironment<String>
) : SerializedService<String> {
    override suspend fun call(endpoint: String, input: CallData<String>): CallData<String> = input

    override suspend fun close() = Unit

    override suspend fun onClose(onClose: suspend () -> Unit) = Unit
}

class BinaryTransformerTest {
    @Test
    fun binaryTransformerRoundTripsByteChannel() = runBlockingUnit {
        val env = ksrpcEnvironment { }
        val service = BinaryTransformerTestService(env)
        val binary = ByteChannel(autoFlush = true)

        val transformed = BinaryTransformer.transform(binary, service)
        val untransformed = BinaryTransformer.untransform(transformed, service)

        assertTrue(transformed.isBinary)
        assertSame(binary, transformed.readBinary())
        assertSame(binary, untransformed)
    }
}
