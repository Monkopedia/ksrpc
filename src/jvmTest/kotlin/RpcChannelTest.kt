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

import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.Test

class RpcChannelTest {

    @Test
    fun testClosingSerializedChannel() = runBlockingUnit {
        val subService = object : Service(), TestSubInterface {
            override suspend fun rpc(u: Pair<String, String>): String {
                return "SUB:${u.first} ${u.second}"
            }
        }
        val rootService = object : Service(), TestRootInterface {
            override suspend fun rpc(u: Pair<String, String>): String {
                return "${u.first} ${u.second}"
            }

            override suspend fun subservice(prefix: String): TestSubInterface {
                return subService
            }
        }
        val mockChannel = mockk<RpcChannel>(relaxed = true)
        mockkObject(TestSubInterface.Companion)

        every {
            TestSubInterface.channel(any())
        } returns mockChannel

        val wrappedService = TestRootInterface.wrap(
            TestRootInterface.serializedChannel(rootService)
        )
        val wrappedSubService = wrappedService.subservice("anything")

        wrappedSubService.close()
        coVerify {
            mockChannel.close()
        }

        unmockkObject(TestSubInterface.Companion)
    }
}
