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
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.functions
import kotlin.reflect.jvm.javaMethod
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer

internal actual class RpcServiceInfo<T : RpcService> actual constructor(
    internal val cls: KClass<T>,
    stubFactory: (RpcServiceChannel) -> T
) : RpcServiceInfoBase<T>(cls, stubFactory) {
    private val signatureInfo by lazy {
        SignatureInfo(this)
    }

    internal fun findEndpoint(endpoint: String): RpcEndpoint<T, *, *> {
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

            override suspend fun close() {
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

        override suspend fun close() {
        }
    })

    fun createEndpoint(
        endpoint: String
    ): RpcServiceInfoBase.RpcEndpoint<T, *, *> {
        synchronized(this) {
            desiredEndpoint = endpoint
            lastCall = null

            rpcServiceInfo.cls.functions.forEach { function ->
                val javaMethod = function.javaMethod ?: return@forEach
                if (javaMethod.parameterCount > 2 || javaMethod.parameterCount == 0) return@forEach
                try {
                    if (javaMethod.parameterCount > 1) {
                        runBlocking {

                            function.callSuspend(proxy, argumentFor(javaMethod.parameterTypes[0]))
                        }
                    } else {
                        javaMethod.invoke(proxy, argumentFor(javaMethod.parameterTypes[0]))
                    }
                } catch (t: NotImplementedError) {
                    // Expected.
                } catch (t: Throwable) {
                    if (t.cause !is NotImplementedError) {
                        println(
                            "Trying $javaMethod ${javaMethod.parameterTypes[0]} " +
                                "${Int::class.java} ${argumentFor(javaMethod.parameterTypes[0])}"
                        )
                        t.printStackTrace()
                    }
                }
                lastCall?.let { endpoint ->
                    if (function.parameters.size > 1) {
                        return@createEndpoint endpoint.copy(
                            suspendFun = { input ->
                                function.callSuspend(this, input)
                            }
                        )
                    } else {
                        return@createEndpoint endpoint.copy(
                            function = { input ->
                                javaMethod.invoke(this, input)
                            }
                        )
                    }
                }
            }
            error("Can't find rpc for $endpoint")
        }
    }

    private fun argumentFor(clazz: Class<*>): Any? {
        return when {
            clazz.isEnum -> {
                null
            }
            clazz.isArray -> null
            clazz == Boolean::class.java -> false
            clazz == Int::class.java -> 0
            clazz == Byte::class.java -> 0.toByte()
            clazz == Char::class.java -> 0.toChar()
            clazz == Short::class.java -> 0.toShort()
            clazz == Long::class.java -> 0L
            clazz == Float::class.java -> 0F
            clazz == Double::class.java -> 0.0
            else -> {
                null
            }
        }
    }
}
