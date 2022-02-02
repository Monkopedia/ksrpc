package com.monkopedia.ksrpc.internal

import com.monkopedia.ksrpc.ChannelClient
import com.monkopedia.ksrpc.ChannelClientProvider
import com.monkopedia.ksrpc.ChannelHost
import com.monkopedia.ksrpc.ChannelHostProvider
import com.monkopedia.ksrpc.Connection
import com.monkopedia.ksrpc.ContextContainer
import com.monkopedia.ksrpc.SerializedService
import internal.MovableInstance
import internal.using
import kotlinx.coroutines.CloseableCoroutineDispatcher
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.native.concurrent.DetachedObjectGraph
import kotlin.native.concurrent.attach
import kotlin.native.concurrent.ensureNeverFrozen

/**
 * Tags instances that are handling thread wrapping in native code already and
 * avoids duplicate wrapping.
 */
internal abstract class ThreadSafe<T>(
    override val context: CoroutineContext,
    val reference: DetachedObjectGraph<T>
) : ContextContainer

@ThreadLocal
private lateinit var threadSafeCache: MutableMap<Any, Any>

@ThreadLocal
private var threadSafeCacheInitialized: Boolean = false

internal actual object ThreadSafeManager {
    val allThreadSafes: MovableInstance<MutableMap<Any, Any>>

    init {
        allThreadSafes = MovableInstance { kotlin.collections.mutableMapOf<kotlin.Any, kotlin.Any>() }
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
        threadSafeCache[instance]?.let {
            return it as T
        }
        println("About to use allThreadSafes")
        allThreadSafes.using { globalMap ->
            println("Using allThreadSafes")
            globalMap[instance]?.let {
                threadSafeCache[instance] = it
                return it as T
            }
            val thread = newSingleThreadContext("thread-safe-${T::class.qualifiedName}")
            val threadSafe = when (instance) {
                is Connection -> createThreadSafeConnection(thread, instance)
                is ChannelHost -> createThreadSafeChannelHost(thread, instance)
                is ChannelClient -> createThreadSafeChannelClient(thread, instance)
                is SerializedService -> createThreadSafeService(thread, instance)
                else -> error("$instance is unsupported for threadSafe operation")
            }
            threadSafeCache[instance] = threadSafe
            globalMap[instance] = threadSafe
            return threadSafe as T
        }
    }

    actual inline fun <reified T : Any> threadSafe(creator: (CoroutineContext) -> T): T {
        if (!threadSafeCacheInitialized) {
            threadSafeCache = mutableMapOf()
            threadSafeCacheInitialized = true
        }
        println("About to use allThreadSafes")
        allThreadSafes.using { globalMap ->
            println("Using allThreadSafes")
            val thread = newSingleThreadContext("thread-safe-${T::class.qualifiedName}")
            val instance = creator(thread)
            instance.ensureNeverFrozen()
            val threadSafe = when (instance) {
                is Connection -> createThreadSafeConnection(thread, instance)
                is ChannelHost -> createThreadSafeChannelHost(thread, instance)
                is ChannelClient -> createThreadSafeChannelClient(thread, instance)
                is SerializedService -> createThreadSafeService(thread, instance)
                else -> error("$instance is unsupported for threadSafe operation")
            }
            globalMap[instance] = threadSafe
            return threadSafe as T
        }
    }

    fun createThreadSafeService(
        thread: CloseableCoroutineDispatcher,
        instance: SerializedService
    ): SerializedService {
        val context = instance.context + thread
        val env = instance.env
        val host = (instance as? ChannelHostProvider)?.host
        val client = (instance as? ChannelClientProvider)?.client
        return ThreadSafeService(context, DetachedObjectGraph { instance }, env, host, client)
    }

    fun createThreadSafeChannelClient(
        thread: CloseableCoroutineDispatcher,
        instance: ChannelClient
    ): ChannelClient {
        val context = instance.context + thread
        val env = instance.env
        return ThreadSafeChannelClient(context, DetachedObjectGraph { instance }, env)
    }

    fun createThreadSafeChannelHost(
        thread: CloseableCoroutineDispatcher,
        instance: ChannelHost
    ): ChannelHost {
        val context = instance.context + thread
        val env = instance.env
        return ThreadSafeChannelHost(context, DetachedObjectGraph { instance }, env)
    }

    fun createThreadSafeConnection(
        thread: CloseableCoroutineDispatcher,
        instance: Connection
    ): Connection {
        val context = instance.context + thread
        val env = instance.env
        return ThreadSafeConnection(context, DetachedObjectGraph { instance }, env)
    }
}

// internal inline fun <T : Any> T.threadSafe(
//    factory: ThreadSafeBuilder<T>.() -> T
// ): T {
//    if (this is ThreadSafe<*>) return this
//    this.ensureNeverFrozen()
//    val threadSafeBuilder = ThreadSafeBuilder(this)
//    return threadSafeBuilder.factory()
// }
//
// internal inline fun <T : Any> threadSafe(
//    baseContext: CoroutineContext,
//    instance: (CoroutineContext) -> T,
//    factory: ThreadSafeBuilder<T>.() -> T
// ): T {
//    val thread = newSingleThreadContext("instance-thread")
//    val instance = instance(baseContext + thread)
//    if (instance is ThreadSafe<*>) {
//        thread.close()
//        return instance
//    }
//    instance.ensureNeverFrozen()
//    val threadSafeBuilder = ThreadSafeBuilder(instance, thread)
//    return threadSafeBuilder.factory()
// }

@ThreadLocal
private var instance: Any? = null

@ThreadLocal
private var initialized: Boolean = false

// internal class ThreadSafeBuilder<T : Any>(
//    instance: T,
//    val thread: CloseableCoroutineDispatcher = newSingleThreadContext("instance-thread")
// ) {
//    val reference = DetachedObjectGraph { instance }
// }

internal suspend inline fun <reified T : Any, R> ThreadSafe<T>.useSafe(crossinline usage: suspend (T) -> R): R {
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
