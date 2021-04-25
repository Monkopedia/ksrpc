/*
 * Copyright 2020 Jason Monk
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

import kotlin.reflect.KClass
import kotlinx.serialization.KSerializer

internal expect class RpcServiceInfo<T : RpcService>(
    cls: KClass<T>,
    stubFactory: (RpcServiceChannel) -> T
) : RpcServiceInfoBase<T>

internal abstract class RpcServiceInfoBase<T : RpcService>(
    cls: KClass<T>,
    private val stubFactory: (RpcServiceChannel) -> T
) {
    internal val endpointLookup = mutableMapOf<String, RpcEndpoint<T, *, *>>()

    fun createStubFor(channel: RpcChannel): T {
        return stubFactory.invoke(RpcServiceChannel(channel))
    }

    abstract fun createChannelFor(service: T): RpcChannel
    abstract suspend fun findEndpoint(str: String): RpcEndpoint<T, *, *>

    data class RpcEndpoint<T : RpcService, I, O>(
        val endpoint: String,
        val isService: Boolean,
        val inputSerializer: KSerializer<I>,
        val outputSerializer: KSerializer<O>,
        val function: (T.(I) -> O)? = null,
        val suspendFun: (suspend T.(I) -> O)? = null,
        val subservice: RpcObject<*>? = null
    ) {
        suspend fun call(channel: RpcChannel, input: Any?): O {
            return channel.call(endpoint, inputSerializer, outputSerializer, input as I)
        }

        suspend fun invoke(service: T, input: I): O {
            return (
                function?.invoke(service, input)
                    ?: suspendFun?.invoke(service, input)
                ) as O
        }
    }
}
