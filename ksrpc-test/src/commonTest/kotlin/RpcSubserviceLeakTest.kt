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
@file:OptIn(KsrpcInternal::class)

package com.monkopedia.ksrpc

import com.monkopedia.ksrpc.RpcFunctionalityTest.TestType
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.annotation.KsrpcInternal
import com.monkopedia.ksrpc.internal.HostSerializedChannelImpl
import com.monkopedia.ksrpc.internal.asClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

@KsService
interface LeakTestSubInterface : RpcService {
    @KsMethod("/ping")
    suspend fun ping(value: String): String

    @KsMethod("/fail")
    suspend fun fail(value: String): String
}

@KsService
interface LeakTestRootInterface : RpcService {
    @KsMethod("/subservice")
    suspend fun subservice(prefix: String): LeakTestSubInterface
}

/**
 * Tracks sub-service lifecycle events by counting creations against closes.
 *
 * The counters are module-level because sub-service instances are instantiated anew by the
 * test doubles on every call — the lifecycle-leak tests assert that, even though creation is
 * unconstrained, every created instance is paired with a `close()` by the time the test's
 * cleanup action completes (either explicit close, parent close cascade, or error-path close).
 *
 * Every test that touches the tracker must call [reset] at the start so that concurrent
 * transport variants (SERIALIZE vs PIPE) of the same open/close test do not cross-contaminate
 * counters. The tests tolerate interleaving because the assertions are only evaluated once a
 * test has observed its own known number of creates/closes via [awaitAllClosed].
 */
private object LeakTracker {
    // The `by atomic(...)` delegate form is what the atomicfu plugin expects — see
    // RpcTypeTest.FakeTestTypes for precedent. Direct `val x = atomic(0)` inside a non-class
    // scope is rejected by the transformer.
    var created: Int by atomic(0)
    var closed: Int by atomic(0)
    var peakLive: Int by atomic(0)
    var callsInFlight: Int by atomic(0)

    fun onCreate() {
        val now = ++created
        val live = now - closed
        // Best-effort peak tracking. Exact peak values are not asserted to an exact number in
        // the tests (only lower/upper bounds), so a racy read-modify-write here is acceptable.
        if (live > peakLive) {
            peakLive = live
        }
    }

    fun onClose() {
        closed++
    }

    fun live(): Int = created - closed

    fun reset() {
        created = 0
        closed = 0
        peakLive = 0
        callsInFlight = 0
    }
}

/**
 * Poll-based wait to avoid relying on any particular dispatcher being awake — cascades from
 * parent close rely on async close observers firing and we do not want a sleep-based wait.
 */
private suspend fun awaitAllClosed(expected: Int, timeoutMillis: Long = 10_000) {
    try {
        withTimeout(timeoutMillis) {
            while (LeakTracker.closed < expected) {
                // yield cooperatively so close callbacks can run on this dispatcher.
                kotlinx.coroutines.yield()
            }
        }
    } catch (_: TimeoutCancellationException) {
        fail(
            "Timed out waiting for sub-service closes: expected=$expected " +
                "created=${LeakTracker.created} closed=${LeakTracker.closed}"
        )
    }
}

private fun leakyRootImpl(): LeakTestRootInterface = object : LeakTestRootInterface {
    override suspend fun subservice(prefix: String): LeakTestSubInterface {
        LeakTracker.onCreate()
        return object : LeakTestSubInterface {
            override suspend fun ping(value: String): String {
                LeakTracker.callsInFlight++
                try {
                    return "$prefix:$value"
                } finally {
                    LeakTracker.callsInFlight--
                }
            }

            override suspend fun fail(value: String): String =
                throw IllegalStateException("intentional-failure:$value")

            override suspend fun close() {
                LeakTracker.onClose()
            }
        }
    }
}

/**
 * Verify open + close cycles of a sub-service do not leak server-side references.
 *
 * The test performs a large number of open/close cycles on a single long-lived connection
 * and asserts that the number of live sub-services is 0 after every round. A leak in
 * `HostSerializedChannelImpl.serviceMap` or anywhere along the close path would surface as
 * a non-zero live count (and the peak-live metric bounds the transient state).
 */
class RpcSubserviceOpenCloseNoLeakTest :
    RpcFunctionalityTest(
        supportedTypes = listOf(TestType.SERIALIZE, TestType.PIPE),
        serializedChannel = {
            LeakTracker.reset()
            leakyRootImpl().serialized(ksrpcEnvironment { })
        },
        verifyOnChannel = { serializedChannel ->
            val stub = serializedChannel.toStub<LeakTestRootInterface, String>()
            val iterations = 50
            repeat(iterations) { i ->
                val sub = stub.subservice("iter-$i")
                assertEquals("iter-$i:hello", sub.ping("hello"))
                sub.close()
            }
            // Ensure any async close observers on the server have flushed.
            awaitAllClosed(iterations)
            assertEquals(
                iterations,
                LeakTracker.created,
                "expected one creation per iteration"
            )
            assertEquals(
                iterations,
                LeakTracker.closed,
                "every sub-service must be closed after its stub close()"
            )
            assertEquals(
                0,
                LeakTracker.live(),
                "no live sub-services should remain after explicit close cycles"
            )
            // Bounded resource usage — at no point should concurrent live services grow beyond
            // what the test explicitly held open.
            assertTrue(
                LeakTracker.peakLive <= 1,
                "peak live should be bounded by the test pattern; was ${LeakTracker.peakLive}"
            )
            assertEquals(
                0,
                LeakTracker.callsInFlight,
                "no calls should remain in-flight after the open/close sweep"
            )
        }
    )

/**
 * Drives the parent close path manually so we can assert the cascade in a deterministic
 * test body without depending on the harness teardown ordering.
 */
class RpcSubserviceParentCloseLeakTest {

    @Test
    fun parentCloseCascadesCloseToAllSubservices() = runBlockingUnit {
        LeakTracker.reset()
        val env = ksrpcEnvironment { }
        val channel = HostSerializedChannelImpl(env)
        channel.registerDefault(leakyRootImpl().serialized(env))
        val stub = channel.asClient.defaultChannel().toStub<LeakTestRootInterface, String>()

        val opened = 8
        val subs = (0 until opened).map { stub.subservice("open-$it") }
        subs.forEachIndexed { i, sub ->
            assertEquals("open-$i:hi", sub.ping("hi"))
        }
        assertEquals(opened, LeakTracker.created)
        assertEquals(0, LeakTracker.closed)

        channel.close()

        awaitAllClosed(opened)
        assertEquals(
            opened,
            LeakTracker.closed,
            "parent close must cascade a close to every registered sub-service"
        )
        assertEquals(
            0,
            LeakTracker.live(),
            "no sub-services should be live after parent close cascade"
        )
    }

    @Test
    fun parentCloseWithNoSubservicesDoesNotInvokeCloses() = runBlockingUnit {
        LeakTracker.reset()
        val env = ksrpcEnvironment { }
        val channel = HostSerializedChannelImpl(env)
        channel.registerDefault(leakyRootImpl().serialized(env))

        channel.close()
        // No work scheduled — absence of sub-service creation means close-counter stays at 0
        // and we don't leak any tracking listeners.
        assertEquals(0, LeakTracker.created)
        assertEquals(0, LeakTracker.closed)
    }

    @Test
    fun closingSubserviceRemovesItBeforeParentClose() = runBlockingUnit {
        LeakTracker.reset()
        val env = ksrpcEnvironment { }
        val channel = HostSerializedChannelImpl(env)
        channel.registerDefault(leakyRootImpl().serialized(env))
        val stub = channel.asClient.defaultChannel().toStub<LeakTestRootInterface, String>()

        val sub1 = stub.subservice("a")
        val sub2 = stub.subservice("b")
        assertEquals("a:x", sub1.ping("x"))
        assertEquals("b:x", sub2.ping("x"))
        sub1.close()
        awaitAllClosed(1)
        assertEquals(
            1,
            LeakTracker.closed,
            "explicit sub-service close must fire before parent close"
        )

        channel.close()
        awaitAllClosed(2)
        assertEquals(
            2,
            LeakTracker.closed,
            "parent close cascade must handle the remaining sub-service"
        )
        // Importantly, closing the parent must not double-close the already-closed sub.
        assertEquals(
            2,
            LeakTracker.created,
            "creation count should equal close count after full teardown"
        )
    }

    @Test
    fun errorOnSubserviceMethodDoesNotLeakReference() = runBlockingUnit {
        LeakTracker.reset()
        val env = ksrpcEnvironment {
            errorListener = ErrorListener { /* swallow intentional failures */ }
        }
        val channel = HostSerializedChannelImpl(env)
        channel.registerDefault(leakyRootImpl().serialized(env))
        val stub = channel.asClient.defaultChannel().toStub<LeakTestRootInterface, String>()

        val iterations = 20
        repeat(iterations) { i ->
            val sub = stub.subservice("err-$i")
            // Invoke the failing endpoint; the server must NOT retain the sub-service simply
            // because one of its calls threw.
            val thrown = runCatching { sub.fail("boom") }.exceptionOrNull()
            assertTrue(
                thrown != null,
                "fail() should raise back to the client; leak risk if silent"
            )
            sub.close()
        }
        awaitAllClosed(iterations)
        assertEquals(
            iterations,
            LeakTracker.closed,
            "error-path invocations must not prevent sub-service close"
        )
        assertEquals(0, LeakTracker.live())

        channel.close()
        // No additional closes should happen — error path left nothing lingering.
        assertEquals(iterations, LeakTracker.closed)
    }

    @Test
    fun parentCloseIsIdempotent() = runBlockingUnit {
        LeakTracker.reset()
        val env = ksrpcEnvironment { }
        val channel = HostSerializedChannelImpl(env)
        channel.registerDefault(leakyRootImpl().serialized(env))
        val stub = channel.asClient.defaultChannel().toStub<LeakTestRootInterface, String>()

        val sub = stub.subservice("once")
        assertEquals("once:ok", sub.ping("ok"))

        channel.close()
        awaitAllClosed(1)
        assertEquals(1, LeakTracker.closed)

        // Second close should be a no-op: must not double-invoke close on any sub-service.
        channel.close()
        assertEquals(
            1,
            LeakTracker.closed,
            "repeat parent close should not re-invoke sub-service close"
        )
    }

    @Test
    fun onCloseObserverFiresOnceAcrossSubserviceLifecycle() = runBlockingUnit {
        LeakTracker.reset()
        val env = ksrpcEnvironment { }
        val channel = HostSerializedChannelImpl(env)
        channel.registerDefault(leakyRootImpl().serialized(env))

        val observed = CompletableDeferred<Unit>()
        // Use an IntArray slot instead of kotlinx.atomicfu.atomic(): the atomicfu transformer
        // rejects atomic() allocations in a local scope. Tests run single-threaded through
        // runBlockingUnit, so a plain slot is safe for this observer-call counter.
        val observerCallCount = intArrayOf(0)
        channel.onClose {
            observerCallCount[0] = observerCallCount[0] + 1
            observed.complete(Unit)
        }

        val stub = channel.asClient.defaultChannel().toStub<LeakTestRootInterface, String>()
        stub.subservice("sub").ping("x")

        channel.close()
        // The observer must fire as part of parent close so that callers can hook teardown.
        withTimeout(5_000) { observed.await() }
        assertEquals(
            1,
            observerCallCount[0],
            "onClose observer should fire exactly once on parent close"
        )
        // Re-closing must not re-fire observers either.
        channel.close()
        assertEquals(
            1,
            observerCallCount[0],
            "repeat parent close should not re-fire onClose observer"
        )
    }

    @Test
    fun openManyThenParentCloseWithNoExplicitChildCloseCascades() = runBlockingUnit {
        LeakTracker.reset()
        val env = ksrpcEnvironment { }
        val channel = HostSerializedChannelImpl(env)
        channel.registerDefault(leakyRootImpl().serialized(env))
        val stub = channel.asClient.defaultChannel().toStub<LeakTestRootInterface, String>()

        val opened = 25
        // Deliberately hold references so the stubs cannot be GC'd mid-test, which would
        // confuse the assertion. We want to prove the host-side cleanup, not client-side GC.
        val held = (0 until opened).map { stub.subservice("held-$it") }
        held.forEachIndexed { i, sub -> assertEquals("held-$i:p", sub.ping("p")) }

        assertEquals(
            opened,
            LeakTracker.peakLive,
            "peak live services should reach the number opened before parent close"
        )

        channel.close()
        awaitAllClosed(opened)
        assertEquals(
            0,
            LeakTracker.live(),
            "parent close must bring live count back to zero regardless of client-side state"
        )
    }
}
