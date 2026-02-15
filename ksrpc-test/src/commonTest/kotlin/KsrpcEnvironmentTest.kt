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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class KsrpcEnvironmentTest {

    @Test
    fun testReconfigureCreatesCopyWithUpdatedFields() {
        val scope1 = CoroutineScope(SupervisorJob())
        val scope2 = CoroutineScope(SupervisorJob())
        val original =
            ksrpcEnvironment {
                defaultScope = scope1
            }

        val updated =
            original.reconfigure {
                defaultScope = scope2
            }

        assertTrue(updated !== original)
        assertEquals(scope1, original.defaultScope)
        assertEquals(scope2, updated.defaultScope)
    }

    @Test
    fun testOnErrorReturnsCopyWithOverriddenListener() {
        val baseErrors = mutableListOf<String>()
        val overriddenErrors = mutableListOf<String>()
        val original =
            ksrpcEnvironment {
                errorListener = ErrorListener { baseErrors.add(it.message ?: "base-null") }
            }
        val updated =
            original.onError(
                ErrorListener { overriddenErrors.add(it.message ?: "updated-null") }
            )

        original.errorListener.onError(IllegalStateException("base"))
        updated.errorListener.onError(IllegalStateException("updated"))

        assertEquals(listOf("base"), baseErrors)
        assertEquals(listOf("updated"), overriddenErrors)
        assertEquals(original.defaultScope, updated.defaultScope)
    }
}
