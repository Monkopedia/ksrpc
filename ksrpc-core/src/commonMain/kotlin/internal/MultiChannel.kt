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

import com.monkopedia.ksrpc.annotation.KsrpcInternal
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@KsrpcInternal
class MultiChannel<T> {

    private var isClosed: Boolean = false
    private var closeException: Throwable? = null
    private val lock = Mutex()
    private val pending = mutableMapOf<String, CompletableDeferred<T>>()
    private val cancelled = mutableSetOf<String>()
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
            // Cancelled receivers leave a tombstone so a late response from the other side
            // is silently dropped rather than raising the "no pending receiver" error: the
            // caller has explicitly disowned this id via [cancelPending].
            if (cancelled.remove(id)) {
                return@withLock
            }
            val pendingItem = pending.remove(id)
            if (pendingItem == null) {
                error("No pending receiver for $id and $response")
            }
            pendingItem.complete(response)
        }
    }

    /**
     * Drop the pending receiver registered under [id] if still present, completing it with
     * [cause] so any lingering awaiter wakes up. Future late `send()` calls for the same id
     * will be silently ignored (treated as a cancelled response).
     */
    suspend fun cancelPending(
        id: String,
        cause: CancellationException = CancellationException("Pending call cancelled")
    ) {
        lock.withLock {
            val removed = pending.remove(id)
            removed?.completeExceptionally(cause)
            // Mark as cancelled so a late-arriving response from the remote side is dropped
            // instead of raising. We always set the tombstone, even if there was no pending
            // entry — the caller may have cancelled before the receive was allocated.
            cancelled.add(id)
        }
    }

    suspend fun allocateReceive(): Pair<Int, Deferred<T>> {
        lock.withLock {
            checkClosed()
            val id = this.id.getAndIncrement()
            val idString = id.toString()
            val completable = CompletableDeferred<T>()
            pending[idString] = completable
            return id to completable
        }
    }

    suspend fun allocateReceiveString(): Pair<String, Deferred<T>> {
        lock.withLock {
            checkClosed()
            val id = this.id.getAndIncrement().toString()
            val completable = CompletableDeferred<T>()
            pending[id] = completable
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
            pending.values.forEach {
                it.completeExceptionally(t ?: CancellationException("Closing MultiChannel"))
            }
            pending.clear()
            cancelled.clear()
        }
    }
}
