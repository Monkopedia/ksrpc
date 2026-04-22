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

import com.monkopedia.ksrpc.RpcObject
import com.monkopedia.ksrpc.SubserviceTransformer
import com.monkopedia.ksrpc.Transformer
import com.monkopedia.ksrpc.annotation.KsrpcInternal
import com.monkopedia.ksrpc.channels.CallData
import com.monkopedia.ksrpc.channels.SerializedService
import kotlinx.coroutines.flow.Flow

/**
 * Transformer emitted by the ksrpc compiler plugin for methods whose `@KsMethod`
 * signature declares `Flow<T>` (in either a parameter or return position — #39).
 *
 * Bridges to the existing [KsFlowService] sub-service protocol:
 * - On [transform] (client-out for params, host-out for returns): wraps the
 *   user's `Flow<T>` in [KsFlowService] via [asKsFlow] and delegates to the
 *   standard [SubserviceTransformer] to register / serialize the sub-service.
 *   If the caller already passes a [KsFlowService] (user opted in to the raw
 *   interface but the plugin's emit site still went through [FlowTransformer]),
 *   the wrapper is passed through as-is.
 * - On [untransform] (client-in for returns, host-in for params): decodes the
 *   sub-service reference into a client-side [KsFlowService] stub and wraps it
 *   in [AutoClosingFlow] so a single terminal operation (`collect`, `toList`,
 *   etc.) tears down the sub-service automatically.
 *
 * The wrapper is symmetric — the same [FlowTransformer] instance is valid for
 * either the `inputTransform` (parameter) or `outputTransform` (return) on a
 * compiler-generated `RpcMethod` without per-side handling.
 *
 * This type is `@KsrpcInternal` — user code should not reference it. The
 * compiler plugin emits constructor calls to it in codegen.
 */
@OptIn(KsrpcInternal::class)
@KsrpcInternal
class FlowTransformer<T>(serviceObject: RpcObject<KsFlowService<T>>) : Transformer<Flow<T>> {
    private val delegate = SubserviceTransformer(serviceObject)

    override val hasContent: Boolean
        get() = delegate.hasContent

    override suspend fun <S> transform(input: Flow<T>, channel: SerializedService<S>): CallData<S> {
        val ksFlow = if (input is KsFlowService<*>) {
            @Suppress("UNCHECKED_CAST")
            input as KsFlowService<T>
        } else {
            input.asKsFlow()
        }
        return delegate.transform(ksFlow, channel)
    }

    override suspend fun <S> untransform(
        data: CallData<S>,
        channel: SerializedService<S>
    ): Flow<T> {
        val service = delegate.untransform(data, channel)
        return AutoClosingFlow(service)
    }
}
