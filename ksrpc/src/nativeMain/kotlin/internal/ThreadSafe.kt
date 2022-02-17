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

import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.SuspendCloseable
import com.monkopedia.ksrpc.SuspendCloseableObservable
import com.monkopedia.ksrpc.channels.ChannelClient
import com.monkopedia.ksrpc.channels.ChannelClientProvider
import com.monkopedia.ksrpc.channels.ChannelHost
import com.monkopedia.ksrpc.channels.ChannelHostProvider
import com.monkopedia.ksrpc.channels.Connection
import com.monkopedia.ksrpc.channels.ConnectionInternal
import com.monkopedia.ksrpc.channels.ConnectionProvider
import com.monkopedia.ksrpc.channels.ContextContainer
import com.monkopedia.ksrpc.channels.SerializedService
import com.monkopedia.ksrpc.channels.SingleChannelConnection
import com.monkopedia.ksrpc.channels.SuspendInit
import com.monkopedia.ksrpc.internal.jsonrpc.JsonRpcChannel
import internal.MovableInstance
import internal.using
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.native.concurrent.DetachedObjectGraph
import kotlin.native.concurrent.TransferMode.UNSAFE
import kotlin.native.concurrent.attach
import kotlin.native.concurrent.ensureNeverFrozen
import kotlin.native.concurrent.freeze
import kotlin.reflect.KClass
import kotlinx.coroutines.CloseableCoroutineDispatcher
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withContext

/**
 * Tags instances that are handling thread wrapping in native code already and
 * avoids duplicate wrapping.
 */
internal class ThreadSafe<T : Any>(
    override val context: CoroutineContext,
    val dispatcher: CloseableCoroutineDispatcher,
    val reference: DetachedObjectGraph<T>,
    override val env: KsrpcEnvironment
) : ContextContainer, KsrpcEnvironment.Element {
    private val threadSafes = MovableInstance { mutableMapOf<KClass<*>, Any>() }

    inline fun <reified T> getWrapper(): T {
        if (T::class == Any::class) return this as T
        if (T::class == ConnectionInternal::class) {
            return threadSafes.using {
                it.getOrPut(Connection::class) {
                    createThreadSafeConnection()
                }
            } as T
        }
        return threadSafes.using {
            it.getOrPut(T::class) {
                when (T::class) {
                    Connection::class -> createThreadSafeConnection()
                    ChannelHost::class -> createThreadSafeChannelHost()
                    ChannelClient::class -> createThreadSafeChannelClient()
                    SerializedService::class -> createThreadSafeService()
                    JsonRpcChannel::class -> createThreadSafeJsonRpcChannel()
                    SingleChannelConnection::class -> createSingleChannelConnection()
                    else -> error("${T::class} is unsupported for threadSafe operation")
                }
            }
        } as T
    }

    fun createThreadSafeService(): SerializedService {
        return ThreadSafeService(this as ThreadSafe<SerializedService>, env).freeze()
    }

    fun createThreadSafeChannelClient(): ChannelClient {
        return ThreadSafeChannelClient(this as ThreadSafe<ChannelClient>, env).freeze()
    }

    fun createThreadSafeChannelHost(): ChannelHost {
        return ThreadSafeChannelHost(this as ThreadSafe<ChannelHost>, env).freeze()
    }

    fun createThreadSafeConnection(): Connection {
        return ThreadSafeConnection(this as ThreadSafe<Connection>, env).freeze()
    }

    fun createThreadSafeJsonRpcChannel(): ThreadSafeJsonRpcChannel {
        return ThreadSafeJsonRpcChannel(this as ThreadSafe<JsonRpcChannel>, env).freeze()
    }

    fun createSingleChannelConnection(): ThreadSafeSingleChannelConnection {
        return ThreadSafeSingleChannelConnection(this as ThreadSafe<SingleChannelConnection>, env)
            .freeze()
    }
}

internal open class ThreadSafeUser<T : Any>(
    val threadSafe: ThreadSafe<T>
) : SuspendCloseable, SuspendInit, SuspendCloseableObservable, ContextContainer by threadSafe {
    final override suspend fun init(): Unit = useSafe {
        (it as? SuspendInit)?.init()
        userCount++
    }

    final override suspend fun close() {
        val needsClose = useSafe {
            (it as? SuspendCloseable)?.close()
            (--userCount == 0)
        }
        if (needsClose) {
            try {
                threadSafe.dispatcher.close()
            } catch (t: Throwable) {
                // Don't mind, just doing best to clean up.
            }
        }
    }

    final override suspend fun onClose(onClose: suspend () -> Unit): Unit = useSafe {
        (it as? SuspendCloseableObservable)?.onClose(onClose)
    }
}

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
            if (this is ThreadSafeUser<*>) {
                threadSafeCache[key] = this.threadSafe
                globalMap[key] = this.threadSafe
                return this.threadSafe.getWrapper()
            }
            val thread = newSingleThreadContext("thread-safe-${T::class.qualifiedName}")
            val context =
                ((instance as? ContextContainer)?.context ?: EmptyCoroutineContext) + thread
            val env = (instance as KsrpcEnvironment.Element).env
            val threadSafe =
                ThreadSafe(context, thread, DetachedObjectGraph(UNSAFE) { instance }, env)
            threadSafeCache[key] = threadSafe
            globalMap[key] = threadSafe
            return threadSafe.getWrapper()
        }
    }

    actual inline fun <reified T : KsrpcEnvironment.Element> threadSafe(
        creator: (CoroutineContext) -> T
    ): T {
        if (!threadSafeCacheInitialized) {
            threadSafeCache = mutableMapOf()
            threadSafeCacheInitialized = true
        }
        allThreadSafes.using { globalMap ->
            val thread = newSingleThreadContext("thread-safe-${T::class.qualifiedName}")
            val instance = creator(thread)
            instance.ensureNeverFrozen()
            val key = (instance as? ThreadSafeKeyed)?.key ?: instance
            val context =
                ((instance as? ContextContainer)?.context ?: EmptyCoroutineContext) + thread
            val env = instance.env
            val threadSafe =
                ThreadSafe(context, thread, DetachedObjectGraph(UNSAFE) { instance }, env)
            globalMap[key] = threadSafe
            threadSafeCache[key] = threadSafe
            return threadSafe.getWrapper()
        }
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

@ThreadLocal
private var userCount: Int = 0

internal suspend inline fun <T : Any, R> ThreadSafeUser<T>.useSafe(
    crossinline usage: suspend (T) -> R
): R = threadSafe.useSafe(usage)

internal suspend inline fun <T : Any, R> ThreadSafe<T>.useSafe(
    crossinline usage: suspend (T) -> R
): R {
    return withContext(context) {
        ensureInitialized()
        usage(instance as T)
    }
}

private suspend inline fun <T : Any> ThreadSafe<T>.ensureInitialized() {
    if (!initialized) {
        instance = (reference as DetachedObjectGraph<Any>).attach()
        userCount = 0
        initialized = true
        (instance as? SuspendCloseableObservable)?.onClose {
            try {
                dispatcher.close()
            } catch (t: Throwable) {
                // Don't mind, just doing best to clean up.
            }
        }
    }
}
