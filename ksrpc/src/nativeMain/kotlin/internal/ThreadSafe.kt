/*
 * Copyright 2021 Jason Monk
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
package com.monkopedia.ksrpc.internal

import com.monkopedia.ksrpc.channels.ChannelClient
import com.monkopedia.ksrpc.channels.ChannelClientProvider
import com.monkopedia.ksrpc.channels.ChannelHost
import com.monkopedia.ksrpc.channels.ChannelHostProvider
import com.monkopedia.ksrpc.channels.Connection
import com.monkopedia.ksrpc.channels.ConnectionProvider
import com.monkopedia.ksrpc.channels.ContextContainer
import com.monkopedia.ksrpc.channels.SerializedService
import com.monkopedia.ksrpc.internal.jsonrpc.JsonRpcChannel
import internal.MovableInstance
import internal.using
import kotlin.coroutines.CoroutineContext
import kotlin.native.concurrent.DetachedObjectGraph
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.attach
import kotlin.native.concurrent.ensureNeverFrozen
import kotlin.native.concurrent.freeze
import kotlinx.coroutines.CloseableCoroutineDispatcher
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withContext
import kotlin.native.concurrent.TransferMode.UNSAFE

/**
 * Tags instances that are handling thread wrapping in native code already and
 * avoids duplicate wrapping.
 */
internal abstract class ThreadSafe<T>(
    override val context: CoroutineContext,
    val reference: DetachedObjectGraph<T>
) : ContextContainer

@ThreadLocal
private var threadSafeCache: MutableMap<Any, Any> = mutableMapOf()

@ThreadLocal
private var threadSafeCacheInitialized: Boolean = false

internal actual object ThreadSafeManager {
    val allThreadSafes: MovableInstance<MutableMap<Any, Any>> = MovableInstance { mutableMapOf() }

    actual inline fun createKey(): Any {
        return Any().freeze()
    }

    // VisibleForTesting
    fun clear() {
        allThreadSafes.using {
            it.clear()
        }
    }

    actual inline fun <reified T : Any> T.threadSafe(): T {
        if (!threadSafeCacheInitialized) {
            threadSafeCache = mutableMapOf()
            threadSafeCacheInitialized = true
        }
        val instance = this
        val key = (this as? ThreadSafeKeyed)?.key ?: this
        threadSafeCache[key]?.let {
            return it as T
        }
        allThreadSafes.using { globalMap ->
            globalMap[key]?.let {
                threadSafeCache[key] = it
                return it as T
            }
            if (this is ThreadSafe<*>) {
                threadSafeCache[key] = this
                globalMap[key] = this
                return this
            }
            val thread = newSingleThreadContext("thread-safe-${T::class.qualifiedName}")
            val threadSafe = when (instance) {
                is Connection -> createThreadSafeConnection(thread, instance)
                is ChannelHost -> createThreadSafeChannelHost(thread, instance)
                is ChannelClient -> createThreadSafeChannelClient(thread, instance)
                is SerializedService -> createThreadSafeService(thread, instance)
                is JsonRpcChannel -> createThreadSafeJsonRpcChannel(thread, instance)
                else -> error("$instance is unsupported for threadSafe operation")
            }
            threadSafeCache[key] = threadSafe
            globalMap[key] = threadSafe
            return threadSafe as T
        }
    }

    actual inline fun <reified T : Any> threadSafe(creator: (CoroutineContext) -> T): T {
        if (!threadSafeCacheInitialized) {
            threadSafeCache = mutableMapOf()
            threadSafeCacheInitialized = true
        }
        allThreadSafes.using { globalMap ->
            val thread = newSingleThreadContext("thread-safe-${T::class.qualifiedName}")
            val instance = creator(thread)
            if (instance == Unit) return Unit as T
            instance.ensureNeverFrozen()
            val key = (instance as? ThreadSafeKeyed)?.key ?: instance
            val threadSafe = when (instance) {
                is Connection -> createThreadSafeConnection(thread, instance)
                is ChannelHost -> createThreadSafeChannelHost(thread, instance)
                is ChannelClient -> createThreadSafeChannelClient(thread, instance)
                is SerializedService -> createThreadSafeService(thread, instance)
                is JsonRpcChannel -> createThreadSafeJsonRpcChannel(thread, instance)
                else -> error("$instance is unsupported for threadSafe operation")
            }
            globalMap[key] = threadSafe
            threadSafeCache[key] = threadSafe
            return threadSafe as T
        }
    }

    fun createThreadSafeService(
        thread: CloseableCoroutineDispatcher,
        instance: SerializedService
    ): SerializedService {
        val env = instance.env
        val context = instance.context + thread
        return ThreadSafeService(
            context,
            DetachedObjectGraph(TransferMode.UNSAFE) { instance },
            env
        ).freeze()
    }

    fun createThreadSafeChannelClient(
        thread: CloseableCoroutineDispatcher,
        instance: ChannelClient
    ): ChannelClient {
        val env = instance.env
        val context = instance.context + thread
        return ThreadSafeChannelClient(
            context,
            DetachedObjectGraph(TransferMode.UNSAFE) { instance },
            env
        ).freeze()
    }

    fun createThreadSafeChannelHost(
        thread: CloseableCoroutineDispatcher,
        instance: ChannelHost
    ): ChannelHost {
        val env = instance.env
        val context = instance.context + thread
        return ThreadSafeChannelHost(
            context,
            DetachedObjectGraph(TransferMode.UNSAFE) { instance },
            env
        ).freeze()
    }

    fun createThreadSafeConnection(
        thread: CloseableCoroutineDispatcher,
        instance: Connection
    ): Connection {
        val env = instance.env
        val context = instance.context + thread
        return ThreadSafeConnection(
            context,
            DetachedObjectGraph(TransferMode.UNSAFE) { instance },
            env
        ).freeze()
    }

    fun createThreadSafeJsonRpcChannel(
        thread: CloseableCoroutineDispatcher,
        instance: JsonRpcChannel
    ): ThreadSafeJsonRpcChannel {
        val env = instance.env
        val context = thread
        return ThreadSafeJsonRpcChannel(
            context,
            DetachedObjectGraph(UNSAFE) { instance },
            env
        ).freeze()
    }

    actual inline fun ThreadSafeKeyedConnection.threadSafeProvider(): ConnectionProvider {
        if (!allThreadSafes.holdsLock) {
            throw IllegalStateException(
                "Cannot create threadSafeProvider outside of threadSafe creation"
            )
        }
        ensureNeverFrozen()
        return ThreadSafeConnectionProvider(key).freeze()
    }

    actual inline fun ThreadSafeKeyedHost.threadSafeProvider(): ChannelHostProvider {
        if (!allThreadSafes.holdsLock) {
            throw IllegalStateException(
                "Cannot create threadSafeProvider outside of threadSafe creation"
            )
        }
        ensureNeverFrozen()
        return ThreadSafeHostProvider(key).freeze()
    }

    actual inline fun ThreadSafeKeyedClient.threadSafeProvider(): ChannelClientProvider {
        if (!allThreadSafes.holdsLock) {
            throw IllegalStateException(
                "Cannot create threadSafeProvider outside of threadSafe creation"
            )
        }
        ensureNeverFrozen()
        return ThreadSafeClientProvider(key).freeze()
    }
}

@ThreadLocal
private var instance: Any? = null

@ThreadLocal
private var initialized: Boolean = false

internal suspend inline fun <reified T : Any, R> ThreadSafe<T>.useSafe(
    crossinline usage: suspend (T) -> R
): R {
    return withContext(context) {
        ensureInitialized()
        usage(instance as T)
    }
}

private inline fun <reified T : Any> ThreadSafe<T>.ensureInitialized() {
    if (!initialized) {
        instance = reference.attach()
        initialized = true
    }
}
