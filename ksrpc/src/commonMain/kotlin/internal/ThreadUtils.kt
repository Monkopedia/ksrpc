package com.monkopedia.ksrpc.internal

import kotlin.coroutines.CoroutineContext

internal expect object ThreadSafeManager {
    inline fun <reified T : Any> T.threadSafe(): T
    inline fun <reified T : Any> threadSafe(creator: (CoroutineContext) -> T): T
}