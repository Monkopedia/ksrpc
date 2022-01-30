package com.monkopedia.ksrpc.internal

// Not needed for JVM.
internal actual inline fun <T : Any> T.threadSafe(
    factory: ThreadSafeBuilder<T>.() -> T
): T = this

internal actual suspend inline fun <reified T : Any, R> ThreadSafeBuilder<T>.useSafe(crossinline usage: suspend (T) -> R): R =
    throw NotImplementedError()

internal actual inline fun <reified T : Any, R> ThreadSafeBuilder<T>.useBlocking(crossinline usage: (T) -> R): R =
    throw NotImplementedError()

internal actual class ThreadSafeBuilder<T : Any>
