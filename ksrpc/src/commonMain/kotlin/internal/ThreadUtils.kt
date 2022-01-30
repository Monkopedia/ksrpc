package com.monkopedia.ksrpc.internal

internal expect inline fun <T : Any> T.threadSafe(
    factory: ThreadSafeBuilder<T>.() -> T
): T

internal expect suspend inline fun <reified T : Any, R> ThreadSafeBuilder<T>.useSafe(crossinline usage: suspend (T) -> R): R
internal expect inline fun <reified T : Any, R> ThreadSafeBuilder<T>.useBlocking(crossinline usage: (T) -> R): R
internal expect class ThreadSafeBuilder<T : Any>
