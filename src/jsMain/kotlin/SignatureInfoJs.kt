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

import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.reflect.KClass
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer

internal actual class RpcServiceInfo<T : RpcService> actual constructor(
    internal val cls: KClass<T>,
    stubFactory: (RpcServiceChannel) -> T
) : RpcServiceInfoBase<T>(cls, stubFactory) {
    private val signatureInfo by lazy {
        SignatureInfo(this)
    }

    internal suspend fun findEndpoint(endpoint: String): RpcEndpoint<T, *, *> {
        return endpointLookup[endpoint] ?: signatureInfo.createEndpoint(endpoint).also {
            endpointLookup[endpoint] = it
        }
    }

    override fun createChannelFor(service: T): RpcChannel {
        return object : RpcChannel {
            override suspend fun <I, O> call(
                endpoint: String,
                inputSer: KSerializer<I>,
                outputSer: KSerializer<O>,
                input: I
            ): O {
                val endpoint = findEndpoint(endpoint) as RpcEndpoint<T, I, O>
                return (
                    endpoint.suspendFun?.invoke(service, input)
                        ?: endpoint.function?.invoke(service, input)
                    ) as O
            }

            override suspend fun <I, O : RpcService> callService(
                endpoint: String,
                retService: RpcObject<O>,
                inputSer: KSerializer<I>,
                input: I
            ): O {
                val endpoint = findEndpoint(endpoint) as RpcEndpoint<T, I, O>
                return (
                    endpoint.suspendFun?.invoke(service, input)
                        ?: endpoint.function?.invoke(service, input)
                    ) as O
            }
        }
    }
}

internal class SignatureInfo<T : RpcService>(private val rpcServiceInfo: RpcServiceInfo<T>) {
    private var desiredEndpoint: String? = null
    private var lastCall: RpcServiceInfoBase.RpcEndpoint<T, Any?, Any?>? = null
    private val proxy = rpcServiceInfo.createStubFor(object : RpcChannel {
        override suspend fun <I, O> call(
            str: String,
            inputSer: KSerializer<I>,
            outputSer: KSerializer<O>,
            input: I
        ): O {
            if (str == desiredEndpoint) {
                lastCall = RpcServiceInfoBase.RpcEndpoint<T, I, O>(str, false, inputSer, outputSer)
                    as RpcServiceInfoBase.RpcEndpoint<T, Any?, Any?>
            }
            throw NotImplementedError()
        }

        override suspend fun <I, O : RpcService> callService(
            str: String,
            service: RpcObject<O>,
            inputSer: KSerializer<I>,
            input: I
        ): O {
            if (str == desiredEndpoint) {
                lastCall = RpcServiceInfoBase.RpcEndpoint<T, I, String>(
                    str,
                    true,
                    inputSer,
                    String.serializer(),
                    subservice = service
                ) as RpcServiceInfoBase.RpcEndpoint<T, Any?, Any?>
            }
            throw NotImplementedError()
        }
    })

    suspend fun createEndpoint(
        endpoint: String
    ): RpcServiceInfoBase.RpcEndpoint<T, *, *> {
        synchronized(this) {
            desiredEndpoint = endpoint
            lastCall = null

            val keys = ownKeys(proxy)
            keys.forEach { key ->
                if (key.toString() == "constructor") return@forEach
                try {
                    proxy.invoke(key.toString(), null)
                } catch (t: Throwable) {
                }
                lastCall?.let { endpoint ->
                    return@createEndpoint endpoint.copy(
                        suspendFun = { input ->
                            this.invoke(key.toString(), input)
                        }
                    )
                }
            }
            error("Can't find rpc for $endpoint")
        }
    }

    val reflect = js("Reflect")

    suspend fun Any.invoke(nameArg: String, arg: Any?): Any {
        return suspendCoroutineUninterceptedOrReturn {
            js("kotlinInvokeArgs = {}")
            val kotlinInvokeArgs = js("kotlinInvokeArgs")
            kotlinInvokeArgs.thisArg = this@invoke
            kotlinInvokeArgs.name = nameArg
            kotlinInvokeArgs.methodArg = arg
            kotlinInvokeArgs.continuation = it
            js(
                "kotlinInvokeArgs.thisArg[kotlinInvokeArgs.name]" +
                    "(kotlinInvokeArgs.methodArg, kotlinInvokeArgs.continuation)"
            )
        }
    }

    inline fun prototypeOf(obj: Any) = reflect.getPrototypeOf(obj)
    inline fun ownKeys(obj: Any) = reflect.ownKeys(prototypeOf(obj)).unsafeCast<Array<dynamic>>()
}

inline fun jsObject(init: dynamic.() -> Unit): dynamic {
    val o = js("{}")
    init(o)
    return o
}
