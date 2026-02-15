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
package com.monkopedia.ksrpc.internal

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MultiChannel<T> {

    private var isClosed: Boolean = false
    private var closeException: Throwable? = null
    private val lock = Mutex()
    private val pending = mutableListOf<Pair<String, CompletableDeferred<T>>>()
    private val id = atomic(1)

    private fun checkClosed() {
        if (isClosed) {
            throw IllegalStateException("MultiChannel ($this) is closed", closeException)
        }
    }

    suspend fun send(id: String, response: T) {
        lock.withLock {
            if (isClosed) {
                return@withLock
            }
            val hasPending = pending.consume(matcher = { it.first == id }) { (_, pendingItem) ->
                pendingItem.complete(response)
            }
            if (!hasPending) {
                error("No pending receiver for $id and $response")
            }
        }
    }

    suspend fun allocateReceive(): Pair<Int, Deferred<T>> {
        lock.withLock {
            checkClosed()
            val id = this.id.getAndIncrement()
            val completable = CompletableDeferred<T>()
            pending.add(id.toString() to completable)
            return id to completable
        }
    }

    suspend fun close(t: CancellationException? = null) {
        lock.withLock {
            if (isClosed) {
                return@withLock
            }
            isClosed = true
            closeException = t
            pending.forEach {
                it.second.completeExceptionally(t ?: CancellationException("Closing MultiChannel"))
            }
            pending.clear()
        }
    }
}

internal inline fun <T> MutableList<T>.consume(
    crossinline matcher: (T) -> Boolean,
    crossinline consumer: (T) -> Unit
): Boolean = removeAll {
    if (matcher(it)) {
        consumer(it)
        true
    } else {
        false
    }
}
