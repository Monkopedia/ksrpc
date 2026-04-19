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

import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.internal.HostSerializedChannelImpl
import com.monkopedia.ksrpc.internal.asClient
import java.lang.ref.WeakReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

@KsService
interface JvmLeakSubInterface : RpcService {
    @KsMethod("/ping")
    suspend fun ping(value: String): String
}

@KsService
interface JvmLeakRootInterface : RpcService {
    @KsMethod("/subservice")
    suspend fun subservice(prefix: String): JvmLeakSubInterface
}

/**
 * JVM-only: after a sub-service is registered and then closed (explicitly or via a cascade
 * from the parent), the server-side service implementation must be weakly-reachable — i.e.
 * the channel must not retain a strong reference after teardown. A lingering strong reference
 * from `HostSerializedChannelImpl.serviceMap` would keep the impl alive indefinitely and
 * eventually surface as an OutOfMemoryError in real apps that produce many short-lived
 * sub-services.
 *
 * GC behaviour is non-deterministic in general, but System.gc() + short sleeps inside a
 * timed retry loop is reliable enough on HotSpot for this assertion. The test will fail
 * fast with a useful message if GC cannot reclaim the reference within the budget.
 */
class RpcSubserviceLeakJvmTest {

    @Test
    fun subserviceIsWeaklyReachableAfterExplicitClose() {
        val (channel, weakImpl) = openThenCloseExplicitly()
        assertGcCollects(weakImpl, "explicit close")
        runBlockingUnit { channel.close() }
    }

    @Test
    fun subserviceIsWeaklyReachableAfterParentCascade() {
        val (channel, weakImpl) = openThenCloseParent()
        assertGcCollects(weakImpl, "parent close cascade")
        // parent already closed; nothing else to tear down
        runBlockingUnit { channel.close() }
    }

    // Helpers deliberately live in methods with no lingering locals pointing at the impl so
    // that a WeakReference is truly the only reference once we drop back to the caller.

    private fun openThenCloseExplicitly():
        Pair<HostSerializedChannelImpl<String>, WeakReference<JvmLeakSubInterface>> {
        val env = ksrpcEnvironment { }
        val channel = HostSerializedChannelImpl(env)
        val serverSide = JvmLeakRootHolder()
        runBlockingUnit {
            channel.registerDefault((serverSide as JvmLeakRootInterface).serialized(env))
            val stub = channel.asClient.defaultChannel()
                .toStub<JvmLeakRootInterface, String>()
            val sub = stub.subservice("explicit")
            assertEquals("explicit:ok", sub.ping("ok"))
            sub.close()
        }
        // Only the WeakReference to the last-created sub impl is kept.
        val weak = WeakReference(serverSide.lastSub!!)
        serverSide.lastSub = null
        return channel to weak
    }

    private fun openThenCloseParent():
        Pair<HostSerializedChannelImpl<String>, WeakReference<JvmLeakSubInterface>> {
        val env = ksrpcEnvironment { }
        val channel = HostSerializedChannelImpl(env)
        val serverSide = JvmLeakRootHolder()
        runBlockingUnit {
            channel.registerDefault((serverSide as JvmLeakRootInterface).serialized(env))
            val stub = channel.asClient.defaultChannel()
                .toStub<JvmLeakRootInterface, String>()
            val sub = stub.subservice("cascade")
            assertEquals("cascade:ok", sub.ping("ok"))
            // Deliberately do NOT close the sub; rely on the parent cascade.
            channel.close()
        }
        val weak = WeakReference(serverSide.lastSub!!)
        serverSide.lastSub = null
        return channel to weak
    }

    private fun assertGcCollects(ref: WeakReference<*>, context: String) {
        repeat(50) {
            if (ref.get() == null) return
            System.gc()
            Thread.sleep(20)
        }
        if (ref.get() != null) {
            fail(
                "Sub-service impl still strongly-reachable after $context and 50 GC rounds; " +
                    "likely held by HostSerializedChannelImpl.serviceMap or a close callback."
            )
        }
    }
}

/**
 * Holds a reference to the last-created sub-service impl so the test can form a
 * WeakReference against it without leaving any strong reference inside the stub-building
 * coroutine frame.
 */
private class JvmLeakRootHolder : JvmLeakRootInterface {
    var lastSub: JvmLeakSubInterface? = null

    override suspend fun subservice(prefix: String): JvmLeakSubInterface {
        val impl = object : JvmLeakSubInterface {
            override suspend fun ping(value: String): String = "$prefix:$value"
        }
        lastSub = impl
        return impl
    }
}
