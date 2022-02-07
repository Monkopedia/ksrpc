package com.monkopedia.ksrpc.internal

import com.monkopedia.ksrpc.ChannelClient
import com.monkopedia.ksrpc.ChannelHost
import com.monkopedia.ksrpc.Connection
import com.monkopedia.ksrpc.ConnectionProvider
import com.monkopedia.ksrpc.ContextContainer
import com.monkopedia.ksrpc.SerializedService
import com.monkopedia.ksrpc.internal.ThreadSafeManager.threadSafe
import internal.MovableInstance
import internal.using
import kotlin.coroutines.CoroutineContext
import kotlin.native.concurrent.DetachedObjectGraph
import kotlin.native.concurrent.attach
import kotlin.native.concurrent.ensureNeverFrozen
import kotlin.native.concurrent.freeze
import kotlinx.coroutines.CloseableCoroutineDispatcher
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withContext
import kotlin.native.concurrent.TransferMode

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
            instance.ensureNeverFrozen()
            val thread = newSingleThreadContext("thread-safe-${T::class.qualifiedName}")
            val threadSafe = when (instance) {
                is Connection -> createThreadSafeConnection(thread, instance)
                is ChannelHost -> createThreadSafeChannelHost(thread, instance)
                is ChannelClient -> createThreadSafeChannelClient(thread, instance)
                is SerializedService -> createThreadSafeService(thread, instance)
                else -> error("$instance is unsupported for threadSafe operation")
            }
            threadSafeCache[key] = threadSafe
            globalMap[key] = threadSafe
            return threadSafe as T
        }
    }

    actual inline fun <reified T : Any> threadSafe(creator: (CoroutineContext) -> T): T {
        println("Start thread safe")
        if (!threadSafeCacheInitialized) {
            threadSafeCache = mutableMapOf()
            threadSafeCacheInitialized = true
        }
        println("Thread safe initialized $allThreadSafes")
        allThreadSafes.using { globalMap ->
            println("Fetched global map ${globalMap}")
            val thread = newSingleThreadContext("thread-safe-${T::class.qualifiedName}")
            println("Created thread $thread")
            val instance = creator(thread)
            instance.ensureNeverFrozen()
            println("Created instance $instance")
            val key = (instance as? ThreadSafeKeyed)?.key ?: instance
            println("Using key $key")
            val threadSafe = when (instance) {
                is Connection -> createThreadSafeConnection(thread, instance)
                is ChannelHost -> createThreadSafeChannelHost(thread, instance)
                is ChannelClient -> createThreadSafeChannelClient(thread, instance)
                is SerializedService -> createThreadSafeService(thread, instance)
                else -> error("$instance is unsupported for threadSafe operation")
            }
            println("Created wrapper")
            globalMap[key] = threadSafe
            threadSafeCache[key] = threadSafe
            println("Returning")
            return threadSafe as T
        }
    }

    fun createThreadSafeService(
        thread: CloseableCoroutineDispatcher,
        instance: SerializedService
    ): SerializedService {
        val env = instance.env
        val context = instance.context + thread
        return ThreadSafeService(context, DetachedObjectGraph(TransferMode.UNSAFE) { instance }, env)
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
        )
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
        )
    }

    fun createThreadSafeConnection(
        thread: CloseableCoroutineDispatcher,
        instance: Connection
    ): Connection {
        val env = instance.env
        val context = instance.context + thread
        println("Moving connection $instance")
        return ThreadSafeConnection(
            context,
            DetachedObjectGraph(TransferMode.UNSAFE) { instance },
            env
        )
    }

    actual inline fun ThreadSafeKeyedConnection.threadSafeProvider(): ConnectionProvider {
        ensureNeverFrozen()
        return ThreadSafeConnectionProvider(key).freeze()
    }

    actual inline fun ThreadSafeKeyedHost.threadSafeProvider(): ThreadSafeHostProvider {
        ensureNeverFrozen()
        return ThreadSafeHostProvider(key).freeze()
    }

    actual inline fun ThreadSafeKeyedClient.threadSafeProvider(): ThreadSafeClientProvider {
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
