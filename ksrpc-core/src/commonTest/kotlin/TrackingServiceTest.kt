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
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.startCoroutineUninterceptedOrReturn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

/**
 * Minimal SerializedService stub: identity-equality only, no actual dispatch. TrackingService
 * only uses it as a set key, so nothing else needs to work.
 */
private class FakeSerializedService : SerializedService<String> {
    override val env: KsrpcEnvironment<String>
        get() = error("not needed for TrackingService tests")

    override suspend fun call(endpoint: String, input: CallData<String>): CallData<String> =
        error("not needed for TrackingService tests")

    override suspend fun close() = Unit

    override suspend fun onClose(onClose: suspend () -> Unit) = Unit
}

/**
 * Records lifecycle events from TrackingService so tests can assert exact ordering and counts.
 */
private class RecordingTrackingService : TrackingService() {
    val events = mutableListOf<String>()
    var firstOpenedCount = 0
    var allClosedCount = 0

    override fun onFirstClientOpened() {
        firstOpenedCount++
        events.add("opened")
    }

    override suspend fun onAllClientsClosed() {
        allClosedCount++
        events.add("closed")
    }
}

/**
 * Synchronously drive a suspend block. TrackingService's callbacks are declared `suspend` but
 * nothing along the paths exercised here actually suspends, so we do not need a real
 * dispatcher or kotlinx-coroutines-test — keeping the dependency surface minimal for
 * multi-platform commonTest.
 */
private fun runTest(block: suspend () -> Unit) {
    var completed = false
    var thrown: Throwable? = null
    val completion = object : Continuation<Unit> {
        override val context: CoroutineContext = EmptyCoroutineContext
        override fun resumeWith(result: Result<Unit>) {
            completed = true
            thrown = result.exceptionOrNull()
        }
    }
    val immediate = block.startCoroutineUninterceptedOrReturn(completion)
    if (immediate !== COROUTINE_SUSPENDED) {
        @Suppress("UNCHECKED_CAST")
        completion.resumeWith(Result.success(immediate as Unit))
    }
    if (!completed) {
        error(
            "Test block did not complete synchronously. TrackingService is not expected to " +
                "suspend under any of the scenarios exercised by these tests."
        )
    }
    thrown?.let { throw it }
}

class TrackingServiceTest {

    @Test
    fun onFirstClientOpenedFiresOnInitialCreate() = runTest {
        val tracking = RecordingTrackingService()
        val s = FakeSerializedService()

        assertEquals(0, tracking.firstOpenedCount)
        tracking.onSerializationCreated(s)
        assertEquals(
            1,
            tracking.firstOpenedCount,
            "onFirstClientOpened must fire when the set transitions 0 -> 1"
        )
        assertEquals(0, tracking.allClosedCount)
    }

    @Test
    fun onAllClientsClosedFiresOnTerminalClose() = runTest {
        val tracking = RecordingTrackingService()
        val s = FakeSerializedService()

        tracking.onSerializationCreated(s)
        assertEquals(0, tracking.allClosedCount)

        tracking.onSerializationClosed(s)
        assertEquals(
            1,
            tracking.allClosedCount,
            "onAllClientsClosed must fire when the set transitions N -> 0"
        )
        assertEquals(listOf("opened", "closed"), tracking.events)
    }

    @Test
    fun onFirstClientOpenedFiresOnlyOnceForMultipleDistinctWrappers() = runTest {
        val tracking = RecordingTrackingService()
        val a = FakeSerializedService()
        val b = FakeSerializedService()

        tracking.onSerializationCreated(a)
        tracking.onSerializationCreated(b)

        assertEquals(
            1,
            tracking.firstOpenedCount,
            "onFirstClientOpened must only fire on the 0 -> 1 transition, not 1 -> 2"
        )
        assertEquals(0, tracking.allClosedCount)
    }

    @Test
    fun onAllClientsClosedOnlyFiresWhenLastWrapperRemoved() = runTest {
        val tracking = RecordingTrackingService()
        val a = FakeSerializedService()
        val b = FakeSerializedService()

        tracking.onSerializationCreated(a)
        tracking.onSerializationCreated(b)

        // Remove one of two — set is still non-empty, terminal callback must not fire yet.
        tracking.onSerializationClosed(a)
        assertEquals(
            0,
            tracking.allClosedCount,
            "onAllClientsClosed must not fire while the set is non-empty"
        )
        assertEquals(1, tracking.firstOpenedCount)

        // Remove the last — terminal callback fires exactly once.
        tracking.onSerializationClosed(b)
        assertEquals(
            1,
            tracking.allClosedCount,
            "onAllClientsClosed must fire exactly once when the last wrapper is removed"
        )
        assertEquals(listOf("opened", "closed"), tracking.events)
    }

    @Test
    fun openOpenCloseCloseOrderingFiresCallbacksOnlyAtBoundaries() = runTest {
        val tracking = RecordingTrackingService()
        val a = FakeSerializedService()
        val b = FakeSerializedService()

        tracking.onSerializationCreated(a) // 0 -> 1  opened
        tracking.onSerializationCreated(b) // 1 -> 2  (no event)
        tracking.onSerializationClosed(a) //  2 -> 1  (no event)
        tracking.onSerializationClosed(b) //  1 -> 0  closed

        assertEquals(1, tracking.firstOpenedCount)
        assertEquals(1, tracking.allClosedCount)
        assertEquals(
            listOf("opened", "closed"),
            tracking.events,
            "lifecycle callbacks must fire exactly at the 0 -> 1 and N -> 0 boundaries"
        )
    }

    @Test
    fun duplicateCreateOfSameWrapperIsNoop() = runTest {
        val tracking = RecordingTrackingService()
        val s = FakeSerializedService()

        tracking.onSerializationCreated(s)
        // Re-adding the identical wrapper: set.add returns false; must not re-fire opened.
        tracking.onSerializationCreated(s)

        assertEquals(
            1,
            tracking.firstOpenedCount,
            "re-registering the identical wrapper must not re-fire onFirstClientOpened"
        )

        // And a single close still transitions to empty and fires closed once.
        tracking.onSerializationClosed(s)
        assertEquals(
            1,
            tracking.allClosedCount,
            "after an ignored duplicate create, one close should still produce exactly one closed"
        )
    }

    @Test
    fun closeOfUnregisteredWrapperIsNoop() = runTest {
        val tracking = RecordingTrackingService()
        val s = FakeSerializedService()

        // Close without a prior create: set.remove returns false; callback must not fire.
        tracking.onSerializationClosed(s)
        assertEquals(
            0,
            tracking.allClosedCount,
            "closing a never-registered wrapper must not fire onAllClientsClosed"
        )
        assertEquals(0, tracking.firstOpenedCount)
    }

    @Test
    fun closeOfUnregisteredWrapperWhileOthersRegisteredIsNoop() = runTest {
        val tracking = RecordingTrackingService()
        val registered = FakeSerializedService()
        val stranger = FakeSerializedService()

        tracking.onSerializationCreated(registered)
        assertEquals(1, tracking.firstOpenedCount)

        // Removing a wrapper that was never added must not decrement or fire the terminal
        // callback — the set still contains `registered`.
        tracking.onSerializationClosed(stranger)
        assertEquals(
            0,
            tracking.allClosedCount,
            "closing an unregistered wrapper must not invalidate the open-count"
        )

        // The legitimate wrapper still closes normally.
        tracking.onSerializationClosed(registered)
        assertEquals(1, tracking.allClosedCount)
    }

    @Test
    fun doubleCloseOfSameWrapperFiresTerminalOnlyOnce() = runTest {
        val tracking = RecordingTrackingService()
        val s = FakeSerializedService()

        tracking.onSerializationCreated(s)
        tracking.onSerializationClosed(s)
        assertEquals(1, tracking.allClosedCount)

        // Calling close a second time on the same (already-removed) wrapper must not re-fire
        // the terminal callback.
        tracking.onSerializationClosed(s)
        assertEquals(
            1,
            tracking.allClosedCount,
            "re-closing an already-removed wrapper must not re-fire onAllClientsClosed"
        )
    }

    @Test
    fun reopenAfterAllClosedFiresCallbacksAgain() = runTest {
        val tracking = RecordingTrackingService()
        val a = FakeSerializedService()
        val b = FakeSerializedService()

        // 0 -> 1 -> 0 -> 1 -> 0
        tracking.onSerializationCreated(a)
        tracking.onSerializationClosed(a)
        tracking.onSerializationCreated(b)
        tracking.onSerializationClosed(b)

        assertEquals(
            2,
            tracking.firstOpenedCount,
            "onFirstClientOpened must fire on every 0 -> 1 transition, including re-entry"
        )
        assertEquals(
            2,
            tracking.allClosedCount,
            "onAllClientsClosed must fire on every N -> 0 transition, including re-entry"
        )
        assertEquals(
            listOf("opened", "closed", "opened", "closed"),
            tracking.events,
            "reopen sequence must produce alternating opened/closed events"
        )
    }

    @Test
    fun interleavedLifecyclesAcrossIndependentTrackersAreIsolated() = runTest {
        // Two tracking services should not share state. Verifies the set is per-instance rather
        // than accidentally shared (e.g., a companion-object mistake).
        val t1 = RecordingTrackingService()
        val t2 = RecordingTrackingService()
        val s1 = FakeSerializedService()
        val s2 = FakeSerializedService()

        t1.onSerializationCreated(s1)
        assertEquals(1, t1.firstOpenedCount)
        assertEquals(0, t2.firstOpenedCount)

        t2.onSerializationCreated(s2)
        assertEquals(1, t2.firstOpenedCount)

        t1.onSerializationClosed(s1)
        assertEquals(1, t1.allClosedCount)
        assertEquals(
            0,
            t2.allClosedCount,
            "closing a wrapper on t1 must not influence t2's state"
        )

        t2.onSerializationClosed(s2)
        assertEquals(1, t2.allClosedCount)
    }

    @Test
    fun defaultOnFirstClientOpenedIsNoop() = runTest {
        // onFirstClientOpened has a default no-op body; subclasses may omit it.
        var closedFired = false
        val silent = object : TrackingService() {
            override suspend fun onAllClientsClosed() {
                closedFired = true
            }
        }
        val s = FakeSerializedService()

        // Should not throw even though onFirstClientOpened was not overridden.
        silent.onSerializationCreated(s)
        silent.onSerializationClosed(s)
        assertTrue(
            closedFired,
            "onAllClientsClosed should still fire when onFirstClientOpened uses default impl"
        )
    }

    @Test
    fun exceptionFromOnFirstClientOpenedPropagatesAndLeavesWrapperRegistered() = runTest {
        val tracking = object : TrackingService() {
            var opened = 0
            var closed = 0
            override fun onFirstClientOpened() {
                opened++
                throw IllegalStateException("intentional-open-failure")
            }

            override suspend fun onAllClientsClosed() {
                closed++
            }
        }
        val s = FakeSerializedService()

        // Failure in onFirstClientOpened must not be silently swallowed.
        val thrown = assertFails { tracking.onSerializationCreated(s) }
        assertEquals("intentional-open-failure", thrown.message)
        assertEquals(1, tracking.opened)

        // The wrapper was added to the set before the callback ran, so a subsequent close
        // still transitions N -> 0 and fires the terminal callback.
        tracking.onSerializationClosed(s)
        assertEquals(
            1,
            tracking.closed,
            "close after a failing open-callback should still fire onAllClientsClosed when the " +
                "wrapper reaches N -> 0"
        )
    }

    @Test
    fun exceptionFromOnAllClientsClosedPropagatesAfterSetEmptied() = runTest {
        val tracking = object : TrackingService() {
            var closed = 0
            override suspend fun onAllClientsClosed() {
                closed++
                throw IllegalStateException("intentional-close-failure")
            }
        }
        val s = FakeSerializedService()

        tracking.onSerializationCreated(s)
        val thrown = assertFails { tracking.onSerializationClosed(s) }
        assertEquals("intentional-close-failure", thrown.message)
        assertEquals(1, tracking.closed)

        // The wrapper was already removed from the set before the throwing callback ran; a new
        // create must fire the opened callback again (proves the set is empty).
        val s2 = FakeSerializedService()
        var openedAgain = false
        val tracking2 = object : TrackingService() {
            override fun onFirstClientOpened() {
                openedAgain = true
            }

            override suspend fun onAllClientsClosed() = Unit
        }
        tracking2.onSerializationCreated(s2)
        assertEquals(true, openedAgain)
    }
}
