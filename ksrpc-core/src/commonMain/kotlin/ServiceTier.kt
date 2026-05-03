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

import com.monkopedia.ksrpc.annotation.KsrpcInternal

/**
 * Describes the minimum transport capability a service requires.
 * The compiler plugin sets this on the generated [RpcObject] companion.
 */
enum class ServiceTier {
    /** Simple input/output — works on all transports. */
    SIMPLE,

    /** Returns sub-services — needs [com.monkopedia.ksrpc.channels.ChannelHost]. */
    HOST,

    /** Accepts sub-service inputs or uses Flow — needs bidirectional [com.monkopedia.ksrpc.channels.Connection]. */
    BIDI
}

/**
 * Verify that [rpcObject]'s [RpcObject.serviceTier] is at most [maxTier]. Throws
 * [IllegalArgumentException] with a descriptive message if the service exceeds the
 * transport's capability.
 */
@KsrpcInternal
fun <T : RpcService> requireTier(
    rpcObject: RpcObject<T>,
    maxTier: ServiceTier,
    transportName: String
) {
    val required = rpcObject.serviceTier
    if (required <= maxTier) return

    throw IllegalArgumentException(
        "${rpcObject.serviceName} requires ${required.name} transport capability, " +
            "but $transportName only supports up to ${maxTier.name}."
    )
}
