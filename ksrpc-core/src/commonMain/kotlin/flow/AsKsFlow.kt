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
import com.monkopedia.ksrpc.asString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

/**
 * Wrap a standard [Flow] in a [KsFlowService] implementation suitable for
 * hosting over ksrpc.
 *
 * Each call to [KsFlowService.startCollection] launches a new coroutine that
 * collects from this flow, forwarding items to the provided
 * [KsFlowCollector]. The returned [KsCollectionToken] can cancel that
 * collection.
 *
 * The [KsFlowService] does NOT auto-close on collection completion -- it
 * stays alive until [KsFlowService.close] is called.
 */
fun <T> Flow<T>.asKsFlow(): KsFlowService<T> = KsFlowServiceImpl(this)

internal class KsFlowServiceImpl<T>(
    private val source: Flow<T>
) : KsFlowService<T> {
    private val parentJob = SupervisorJob()

    override suspend fun startCollection(collector: KsFlowCollector<T>): KsCollectionToken {
        val scope = CoroutineScope(coroutineContext + parentJob)
        val job = scope.launch {
            try {
                source.collect { item ->
                    collector.onItem(item)
                }
                collector.onComplete(Unit)
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Collection was cancelled -- don't send onError for cancellation
                throw e
            } catch (e: Throwable) {
                collector.onError(RpcFailure(e.asString))
            }
        }
        return object : KsCollectionToken {
            override suspend fun cancelCollection(u: Unit) {
                job.cancel()
            }
        }
    }

    override suspend fun collect(collector: FlowCollector<T>) {
        source.collect(collector)
    }

    override suspend fun close() {
        parentJob.cancel()
    }
}
