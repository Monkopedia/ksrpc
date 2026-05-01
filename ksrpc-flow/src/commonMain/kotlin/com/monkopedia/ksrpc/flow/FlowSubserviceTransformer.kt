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

import com.monkopedia.ksrpc.BaseSubserviceTransformer
import com.monkopedia.ksrpc.RpcObject
import com.monkopedia.ksrpc.annotation.KsrpcInternal
import kotlinx.coroutines.flow.Flow

/**
 * Transformer emitted by the ksrpc compiler plugin for methods whose `@KsMethod`
 * signature declares `Flow<T>` (in either a parameter or return position — #39).
 *
 * A [BaseSubserviceTransformer] specialization: the wire representation is a
 * [KsFlowService] sub-service reference, but the user-facing type is the plain
 * `kotlinx.coroutines.flow.Flow<T>`. The base class supplies the register /
 * lookup machinery; this subclass only bridges between `Flow<T>` and
 * `KsFlowService<T>`:
 * - [toService] wraps the user's `Flow<T>` in [KsFlowService] via [asKsFlow]
 *   (or passes a pre-existing [KsFlowService] through unchanged, e.g. when the
 *   user opted into the raw interface but the plugin's emit site still went
 *   through this transformer).
 * - [fromService] wraps the decoded client stub in [AutoClosingFlow] so a
 *   single terminal operation (`collect`, `toList`, etc.) tears down the
 *   sub-service automatically.
 *
 * Because this extends [BaseSubserviceTransformer], introspection sees it as a
 * sub-service transformer and surfaces the underlying
 * [KsFlowService] name — no delegation peel required.
 *
 * This type is `@KsrpcInternal` — user code should not reference it. The
 * compiler plugin emits constructor calls to it in codegen.
 */
@OptIn(KsrpcInternal::class)
@KsrpcInternal
class FlowSubserviceTransformer<T>(
    override val serviceObject: RpcObject<KsFlowService<T>>,
    private val elementSerializer: kotlinx.serialization.KSerializer<T>? = null
) : BaseSubserviceTransformer<KsFlowService<T>, Flow<T>>() {
    override val typeArgSerializers: List<kotlinx.serialization.KSerializer<*>>
        get() = listOfNotNull(elementSerializer)
    override fun toService(value: Flow<T>): KsFlowService<T> = if (value is KsFlowService<*>) {
        @Suppress("UNCHECKED_CAST")
        value as KsFlowService<T>
    } else {
        value.asKsFlow()
    }

    override fun fromService(service: KsFlowService<T>): Flow<T> = AutoClosingFlow(service)
}
