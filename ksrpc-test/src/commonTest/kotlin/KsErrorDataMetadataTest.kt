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

import com.monkopedia.ksrpc.annotation.KsErrorData
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.serialization.Serializable

@Serializable
data class TestErrorDetail(val errorCode: String, val description: String)

@KsService
interface ErrorDataTestService : RpcService {
    @KsMethod("/plain")
    suspend fun plain(input: String): String

    @KsMethod("/with_error_data")
    @KsErrorData(errorType = TestErrorDetail::class)
    suspend fun withErrorData(input: String): String
}

class KsErrorDataMetadataTest {
    private val rpcObject = rpcObject<ErrorDataTestService>()

    @Test
    fun methodWithoutKsErrorDataHasNullErrorDataType() {
        val method = rpcObject.findEndpoint("plain")
        assertNull(method.errorDataType)
    }

    @Test
    fun methodWithKsErrorDataHasCorrectErrorDataType() {
        val method = rpcObject.findEndpoint("with_error_data")
        val errorType = method.errorDataType
        assertNotNull(errorType)
        assertEquals(TestErrorDetail::class, errorType)
    }

    @Test
    fun ksErrorDataMetadataIsCapturedWithCorrectFqName() {
        val method = rpcObject.findEndpoint("with_error_data")
        val meta = method.metadata("com.monkopedia.ksrpc.annotation.KsErrorData")
        assertNotNull(meta)
        val errorTypeArg = meta.argument("errorType") as? MetadataValue.KClassValue
        assertNotNull(errorTypeArg)
        assertEquals(TestErrorDetail::class, errorTypeArg.kClass)
    }
}
