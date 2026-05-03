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
 * Compute the minimum [ServiceTier] required by this [RpcObject]'s methods.
 */
@KsrpcInternal
fun <T : RpcService> RpcObject<T>.requiredTier(): ServiceTier {
    var tier = ServiceTier.SIMPLE
    for (endpoint in endpoints) {
        val method = findEndpoint(endpoint)
        if (method.inputTransform is BaseSubserviceTransformer<*, *>) {
            return ServiceTier.BIDI // sub-service input → bidi, can't get higher
        }
        if (method.outputTransform is BaseSubserviceTransformer<*, *>) {
            tier = maxOf(tier, ServiceTier.HOST)
        }
    }
    return tier
}

/**
 * Verify that [rpcObject]'s required tier is at most [maxTier]. Throws
 * [IllegalArgumentException] with a descriptive message naming the offending
 * method if the service exceeds the transport's capability.
 */
@KsrpcInternal
fun <T : RpcService> requireTier(
    rpcObject: RpcObject<T>,
    maxTier: ServiceTier,
    transportName: String
) {
    val required = rpcObject.requiredTier()
    if (required <= maxTier) return

    // Find the first offending method for a helpful error message.
    for (endpoint in rpcObject.endpoints) {
        val method = rpcObject.findEndpoint(endpoint)
        if (required == ServiceTier.BIDI && method.inputTransform is BaseSubserviceTransformer<*, *>) {
            throw IllegalArgumentException(
                "${rpcObject.serviceName} requires bidirectional transport " +
                    "(method '$endpoint' accepts a sub-service input), " +
                    "but $transportName does not support this."
            )
        }
        if (required == ServiceTier.BIDI && method.outputTransform is BaseSubserviceTransformer<*, *>) {
            throw IllegalArgumentException(
                "${rpcObject.serviceName} requires bidirectional transport " +
                    "(method '$endpoint' returns a bidirectional sub-service), " +
                    "but $transportName does not support this."
            )
        }
        if (required == ServiceTier.HOST && method.outputTransform is BaseSubserviceTransformer<*, *>) {
            throw IllegalArgumentException(
                "${rpcObject.serviceName} requires HOST transport " +
                    "(method '$endpoint' returns a sub-service), " +
                    "but $transportName only supports SIMPLE services."
            )
        }
    }
    // Generic fallback
    throw IllegalArgumentException(
        "${rpcObject.serviceName} requires ${required.name} transport capability, " +
            "but $transportName only supports up to ${maxTier.name}."
    )
}
