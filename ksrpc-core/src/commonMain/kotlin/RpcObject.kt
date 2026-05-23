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
package com.monkopedia.ksrpc

import com.monkopedia.ksrpc.channels.SerializedService

/**
 * Interface for generated companions of [RpcService].
 *
 * @sample com.monkopedia.ksrpc.samples.basicServiceDeclaration
 */
interface RpcObject<T : RpcService> {
    val serviceName: String
    val endpoints: List<String>

    /**
     * The minimum transport capability tier this service requires, as determined
     * by the compiler plugin from the service's method signatures. Used by
     * transport registration checks to reject services that can't work on a
     * given channel.
     */
    val serviceTier: ServiceTier get() = ServiceTier.SIMPLE

    fun <S> createStub(channel: SerializedService<S>): T
    fun findEndpoint(endpoint: String): RpcMethod<*, *, *>
}
