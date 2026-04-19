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

import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.channels.CallData
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.builtins.serializer

@KsService
interface UnhandledHandlerService : RpcService {
    @KsMethod("/known")
    suspend fun known(input: String): String
}

/**
 * Verifies that a service without [UnhandledMethodHandler] keeps the current
 * endpoint-not-found behavior when called with an unregistered method.
 */
class RpcUnhandledMethodHandlerNotImplementedTest :
    RpcFunctionalityTest(
        serializedChannel = {
            val impl = object : UnhandledHandlerService {
                override suspend fun known(input: String): String = "known:$input"
            }
            impl.serialized(ksrpcEnvironment { })
        },
        verifyOnChannel = { channel ->
            val response = channel.call("missing", CallData.create("ignored"))
            assertTrue(channel.env.serialization.isError(response))
            val exception = channel.env.serialization.decodeErrorCallData(response)
            val message = exception.message ?: ""
            assertTrue(
                message.contains("Unknown endpoint: missing"),
                message
            )
        }
    )

/**
 * Verifies that when a service implements [UnhandledMethodHandler], unknown
 * endpoints are routed to [UnhandledMethodHandler.onUnhandled] and the handler's
 * serialized return value reaches the caller.
 */
class RpcUnhandledMethodHandlerImplementedTest :
    RpcFunctionalityTest(
        serializedChannel = {
            val env = ksrpcEnvironment { }
            val impl = object :
                UnhandledHandlerService,
                UnhandledMethodHandler {
                override suspend fun known(input: String): String = "known:$input"

                override suspend fun <T> onUnhandled(
                    method: String,
                    input: CallData<T>
                ): CallData<T> {
                    @Suppress("UNCHECKED_CAST")
                    val stringInput = input as CallData<String>
                    val payload = env.serialization.decodeCallData(
                        String.serializer(),
                        stringInput
                    )
                    @Suppress("UNCHECKED_CAST")
                    return env.serialization.createCallData(
                        String.serializer(),
                        "handled:$method:$payload"
                    ) as CallData<T>
                }
            }
            impl.serialized(env)
        },
        verifyOnChannel = { channel ->
            // Known endpoint still dispatches normally.
            val knownResponse = channel.call(
                "known",
                channel.env.serialization.createCallData(
                    String.serializer(),
                    "hi"
                )
            )
            assertFalse(channel.env.serialization.isError(knownResponse))
            assertEquals(
                "known:hi",
                channel.env.serialization.decodeCallData(
                    String.serializer(),
                    knownResponse
                )
            )

            // Unknown endpoint is routed to onUnhandled and its return value propagates.
            val unknownResponse = channel.call(
                "missing",
                channel.env.serialization.createCallData(
                    String.serializer(),
                    "payload"
                )
            )
            assertFalse(channel.env.serialization.isError(unknownResponse))
            assertEquals(
                "handled:missing:payload",
                channel.env.serialization.decodeCallData(
                    String.serializer(),
                    unknownResponse
                )
            )
        }
    )

/**
 * Verifies that exceptions thrown from [UnhandledMethodHandler.onUnhandled] are
 * reported back to the caller as remote errors, matching the normal handler path.
 */
class RpcUnhandledMethodHandlerThrowsTest :
    RpcFunctionalityTest(
        serializedChannel = {
            val impl = object :
                UnhandledHandlerService,
                UnhandledMethodHandler {
                override suspend fun known(input: String): String = "known:$input"

                override suspend fun <T> onUnhandled(
                    method: String,
                    input: CallData<T>
                ): CallData<T> {
                    throw IllegalStateException("boom:$method")
                }
            }
            impl.serialized(ksrpcEnvironment { })
        },
        verifyOnChannel = { channel ->
            val response = channel.call(
                "missing",
                channel.env.serialization.createCallData(
                    String.serializer(),
                    "payload"
                )
            )
            assertTrue(channel.env.serialization.isError(response))
            val exception = channel.env.serialization.decodeErrorCallData(response)
            val message = exception.message ?: ""
            assertTrue(
                message.contains("boom:missing"),
                message
            )
        }
    )
