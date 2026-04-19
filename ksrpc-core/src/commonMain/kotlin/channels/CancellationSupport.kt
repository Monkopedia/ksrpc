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
package com.monkopedia.ksrpc.channels

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext

/**
 * Transport-level cancellation primitive.
 *
 * Transports that can propagate cancellation across the wire implement this interface. Core
 * uses [awaitRequestCancellable] to convert the caller coroutine's cancellation into a
 * [sendCancel] to the remote side, and server-side dispatchers use [registerHandler] /
 * [cancelHandler] to tie incoming cancel signals to the currently running handler `Job`.
 *
 * Implementations should treat [sendCancel] as best-effort — the transport may already be
 * closed by the time the caller is cancelled, and callers rely on `CancellationException`
 * propagating regardless.
 */
interface CancellationSupport {
    /**
     * Emit a cancellation signal for the given in-flight request to the remote endpoint.
     *
     * Must not throw for normal transport-closed conditions — the local coroutine is already
     * being cancelled and will receive a `CancellationException` anyway.
     */
    suspend fun sendCancel(callId: RpcCallId)

    /**
     * Register a server-side handler `Job` under [callId] so that a later cancel signal from
     * the remote side can cancel the correct handler.
     *
     * The dispatcher is expected to remove the entry once the handler completes (via
     * [unregisterHandler]).
     */
    fun registerHandler(callId: RpcCallId, job: Job)

    /**
     * Remove a previously registered handler. Safe to call for ids that were never registered.
     */
    fun unregisterHandler(callId: RpcCallId)

    /**
     * Cancel the handler previously registered under [callId], if any.
     */
    fun cancelHandler(callId: RpcCallId, cause: CancellationException? = null)
}

/**
 * Await [response] and, if the awaiting coroutine is cancelled before completion, invoke
 * [CancellationSupport.sendCancel] for [callId] before re-throwing.
 *
 * This is the client-side half of the cancellation mechanism — transports that track pending
 * calls should route their stub-side `call(...)` through this helper so that cancellation is
 * forwarded to the remote end.
 */
suspend fun <T> CancellationSupport.awaitRequestCancellable(
    callId: RpcCallId,
    response: Deferred<T>
): T = try {
    response.await()
} catch (t: CancellationException) {
    // Best-effort remote signal — swallow transport failures. Must run in NonCancellable
    // because the surrounding coroutine is already cancelled, and any suspension point in
    // [sendCancel] (e.g. sendLock acquisition) would otherwise rethrow immediately.
    try {
        withContext(NonCancellable) {
            sendCancel(callId)
        }
    } catch (_: Throwable) {
    }
    throw t
}

/**
 * Utility to bind the currently executing coroutine's [Job] to [callId] on a
 * [CancellationSupport] so that an incoming remote cancel signal cancels this coroutine.
 *
 * Unregisters on return.
 */
suspend inline fun <T> CancellationSupport.withHandlerRegistered(
    callId: RpcCallId,
    block: () -> T
): T {
    val job = currentCoroutineContext()[Job]
        ?: error("CancellationSupport.withHandlerRegistered requires a Job in the context")
    registerHandler(callId, job)
    try {
        return block()
    } finally {
        unregisterHandler(callId)
    }
}
