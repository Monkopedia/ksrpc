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
import com.monkopedia.ksrpc.annotation.KsNotification
import com.monkopedia.ksrpc.annotation.KsService

/**
 * Sub-service interface for receiving flow items, errors, and completion signals.
 *
 * Each active collection gets its own [KsFlowCollector] instance. The server
 * calls [onItem] for each element, [onComplete] when the flow finishes
 * normally, and [onError] when it finishes with an exception.
 *
 * [onError] and [onComplete] are terminal — after either is called, no more
 * [onItem] calls will be made on this collector.
 */
@KsService
interface KsFlowCollector<T> : RpcService {
    /**
     * Deliver an item from the flow to the collector.
     *
     * This is a suspend call through the sub-service path, providing natural
     * back-pressure — the server blocks until the client processes the item.
     */
    @KsMethod("/onItem")
    suspend fun onItem(item: T)

    /**
     * Signal that the flow terminated with an error.
     *
     * This is a notification (fire-and-forget). Terminal for this collection.
     */
    @KsMethod("/onError")
    @KsNotification
    suspend fun onError(error: RpcFailure)

    /**
     * Signal that the flow completed normally.
     *
     * This is a notification (fire-and-forget). Terminal for this collection.
     */
    @KsMethod("/onComplete")
    @KsNotification
    suspend fun onComplete()
}
