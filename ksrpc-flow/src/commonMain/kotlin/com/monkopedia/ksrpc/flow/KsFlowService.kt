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
package com.monkopedia.ksrpc.flow

import com.monkopedia.ksrpc.RpcFailure
import com.monkopedia.ksrpc.RpcService
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.toException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector

/**
 * Sub-service interface that bridges a [Flow] over the ksrpc sub-service
 * protocol.
 *
 * Extends both [Flow] (so it can be collected with standard coroutines APIs)
 * and [RpcService] (for sub-service lifecycle management).
 *
 * **Common case (`Flow<T>` in signature):** The compiler wraps returns in
 * [AutoClosingFlow] so the sub-service is closed after collection. Single-use.
 *
 * **Advanced case (`KsFlowService<T>` in signature):** The client gets the
 * raw service, can call [collect] multiple times, and must call [close]
 * explicitly when done.
 *
 * [KsFlowService] does NOT auto-close on collection completion — it stays
 * alive until [close] is called explicitly or its parent connection closes.
 */
@KsService
interface KsFlowService<T> :
    Flow<T>,
    RpcService {
    /**
     * Start collecting from this flow service.
     *
     * The caller provides a [KsFlowCollector] sub-service that will receive
     * items, completion, and error signals. Returns a [KsCollectionToken] that
     * can be used to cancel the collection.
     *
     * Multiple calls are allowed — each gets its own collection job and token.
     */
    @KsMethod("/collect")
    suspend fun startCollection(collector: KsFlowCollector<T>): KsCollectionToken

    /**
     * Default [Flow.collect] implementation: wraps [collector] in a
     * [KsFlowCollector], calls [startCollection], and suspends until
     * `onComplete` or `onError`. On coroutine cancellation, cancels the
     * underlying collection via [KsCollectionToken.cancelCollection].
     */
    override suspend fun collect(collector: FlowCollector<T>) {
        val completion = CompletableDeferred<Unit>()
        val collectorImpl = object : KsFlowCollector<T> {
            override suspend fun onItem(item: T) {
                collector.emit(item)
            }

            override suspend fun onError(error: RpcFailure) {
                completion.completeExceptionally(error.toException())
            }

            override suspend fun onComplete() {
                completion.complete(Unit)
            }

            override suspend fun close() = Unit
        }
        val token = startCollection(collectorImpl)
        try {
            completion.await()
        } catch (e: CancellationException) {
            try {
                token.cancelCollection()
            } catch (_: Throwable) {
                // best effort — we're cancelling anyway
            }
            throw e
        }
    }
}
