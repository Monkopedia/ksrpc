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

import com.monkopedia.ksrpc.internal.MultiChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException

class MultiChannelCoreTest {

    @Test
    fun testSendCompletesMatchingPendingReceive() = runBlockingUnit {
        val channel = MultiChannel<String>()
        val (id, pending) = channel.allocateReceive()

        channel.send(id.toString(), "value")

        assertEquals("value", pending.await())
    }

    @Test
    fun testCloseCancelsPendingReceive() = runBlockingUnit {
        val channel = MultiChannel<String>()
        val (_, pending) = channel.allocateReceive()

        channel.close()

        assertFailsWith<CancellationException> { pending.await() }
    }

    @Test
    fun testSendWithoutPendingReceiverThrows() = runBlockingUnit {
        val channel = MultiChannel<String>()
        val (_, pending) = channel.allocateReceive()

        val thrown =
            assertFailsWith<IllegalStateException> {
                channel.send("no-such-id", "value")
            }
        assertTrue(thrown.message?.contains("No pending receiver") == true)

        channel.close()
        assertFailsWith<CancellationException> { pending.await() }
    }

    @Test
    fun testAllocateReceiveAfterCloseThrows() = runBlockingUnit {
        val channel = MultiChannel<String>()
        channel.close()

        val thrown =
            assertFailsWith<IllegalStateException> {
                channel.allocateReceive()
            }
        assertTrue(thrown.message?.contains("closed") == true)
    }

    @Test
    fun testCloseWithCustomCancellationPropagatesToPending() = runBlockingUnit {
        val channel = MultiChannel<String>()
        val (_, pending) = channel.allocateReceive()
        val cause = CancellationException("custom-close")

        channel.close(cause)

        val thrown = assertFailsWith<CancellationException> { pending.await() }
        assertTrue(thrown.message?.contains("custom-close") == true)
    }

    @Test
    fun testCloseCancelsAllPendingReceivesWithSameCause() = runBlockingUnit {
        val channel = MultiChannel<String>()
        val (_, pending1) = channel.allocateReceive()
        val (_, pending2) = channel.allocateReceive()
        val cause = CancellationException("shared-close")

        channel.close(cause)

        val first = assertFailsWith<CancellationException> { pending1.await() }
        val second = assertFailsWith<CancellationException> { pending2.await() }
        assertTrue(first.message?.contains("shared-close") == true)
        assertTrue(second.message?.contains("shared-close") == true)
    }

    @Test
    fun testCloseIsIdempotentAndSendAfterCloseIsIgnored() = runBlockingUnit {
        val channel = MultiChannel<String>()
        val (id, pending) = channel.allocateReceive()

        channel.close()
        channel.close()
        channel.send(id.toString(), "ignored")

        assertFailsWith<CancellationException> { pending.await() }
    }

    @Test
    fun testAllocateReceiveIdsAreUniqueAndIncreasing() = runBlockingUnit {
        val channel = MultiChannel<String>()
        val (id1, pending1) = channel.allocateReceive()
        val (id2, pending2) = channel.allocateReceive()

        assertTrue(id2 > id1)
        channel.send(id1.toString(), "first")
        channel.send(id2.toString(), "second")
        assertEquals("first", pending1.await())
        assertEquals("second", pending2.await())
    }

    @Test
    fun testAllocateReceiveAfterCloseIncludesCloseCause() = runBlockingUnit {
        val channel = MultiChannel<String>()
        val cause = CancellationException("closed-by-test")
        channel.close(cause)

        val thrown =
            assertFailsWith<IllegalStateException> {
                channel.allocateReceive()
            }
        assertTrue(thrown.cause is CancellationException)
        assertTrue(thrown.cause?.message?.contains("closed-by-test") == true)
    }

    @Test
    fun testUnmatchedSendDoesNotConsumePendingReceiver() = runBlockingUnit {
        val channel = MultiChannel<String>()
        val (id, pending) = channel.allocateReceive()

        assertFailsWith<IllegalStateException> {
            channel.send("missing-id", "ignored")
        }

        channel.send(id.toString(), "value")
        assertEquals("value", pending.await())
    }

    @Test
    fun testSecondCloseDoesNotOverrideOriginalCloseCause() = runBlockingUnit {
        val channel = MultiChannel<String>()
        val firstCause = CancellationException("first-close")
        val secondCause = CancellationException("second-close")

        channel.close(firstCause)
        channel.close(secondCause)

        val thrown =
            assertFailsWith<IllegalStateException> {
                channel.allocateReceive()
            }
        assertTrue(thrown.cause?.message?.contains("first-close") == true)
        assertTrue(thrown.cause?.message?.contains("second-close") != true)
    }
}
