/*
 * Copyright 2020 Jason Monk
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

import kotlin.reflect.jvm.jvmName
import kotlin.test.Test
import kotlin.test.assertEquals

class UriTest {

    class Implementation : Service(), TestInterface {
        override suspend fun rpc(u: Pair<String, String>): String {
            return u.second + " " + u.first
        }
    }

    @Test
    fun testLocalService() = runBlockingUnit {
        val clsString = Implementation::class.jvmName
        val uri = "local://$clsString"
        val channel = uri.toKsrpcUri().connect()
        val service = TestInterface.wrap(channel)

        assertEquals("world hello", service.rpc("hello" to "world"))
    }
}
