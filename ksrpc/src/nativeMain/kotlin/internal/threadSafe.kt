package com.monkopedia.ksrpc.internal

import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.native.concurrent.DetachedObjectGraph
import kotlin.native.concurrent.attach
import kotlin.native.concurrent.ensureNeverFrozen

internal actual inline fun <T : Any> T.threadSafe(
    factory: ThreadSafeBuilder<T>.() -> T
): T {
    this.ensureNeverFrozen()
    val threadSafeBuilder = ThreadSafeBuilder(this)
    return threadSafeBuilder.factory()
}

@ThreadLocal
private var instance: Any? = null
@ThreadLocal
private var initialized: Boolean = false

internal actual class ThreadSafeBuilder<T : Any>(instance: T) {
    val thread = newSingleThreadContext("instance-thread")
    val reference = DetachedObjectGraph { instance }
}

internal actual suspend inline fun <reified T : Any, R> ThreadSafeBuilder<T>.useSafe(crossinline usage: suspend (T) -> R): R {
    return withContext(thread) {
        if (!initialized) {
            instance = reference.attach()
            initialized = true
        }
        useSafeImpl {
            usage(it)
        }
    }
}

internal suspend fun <T : Any, R> ThreadSafeBuilder<T>.useSafeImpl(usage: suspend (T) -> R): R {
    return withContext(thread) {
        usage(instance as T)
    }
}

internal actual inline fun <reified T : Any, R> ThreadSafeBuilder<T>.useBlocking(crossinline usage: (T) -> R): R {
    // Hacks.
    return runBlocking {
        useSafe {
            usage(it)
        }
    }
}