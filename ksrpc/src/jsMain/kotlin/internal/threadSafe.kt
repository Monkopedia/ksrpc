package com.monkopedia.ksrpc.internal

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

internal actual object ThreadSafeManager {
    actual inline fun <reified T : Any> T.threadSafe(): T = this
    actual inline fun <reified T : Any> threadSafe(creator: (CoroutineContext) -> T): T =
        creator(EmptyCoroutineContext)
}
