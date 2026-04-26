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
@file:OptIn(KsrpcInternal::class)

package com.monkopedia.ksrpc

import com.monkopedia.ksrpc.annotation.KsError
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.annotation.KsrpcInternal
import com.monkopedia.ksrpc.ktor.KSRPC_ERROR_CODE_HEADER
import com.monkopedia.ksrpc.ktor.asHttpChannelClient
import com.monkopedia.ksrpc.ktor.serveHttp
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.fail
import kotlinx.serialization.Serializable

@Serializable
class HttpInitError(val retry: Boolean, val reason: String) : RuntimeException() {
    override val message: String get() = "init failed: $reason"
}

@KsService
interface HttpTypedErrorService : RpcService {
    @KsMethod("/init")
    @KsError(code = 100, type = HttpInitError::class)
    suspend fun init(input: String): String

    @KsMethod("/missing-only")
    suspend fun missingOnly(input: String): String
}

class HttpTypedErrorRoutingTest {

    /**
     * Verifies the default error-code-to-status map: thrown
     * [com.monkopedia.ksrpc.RpcEndpointException] surfaces as HTTP 404, the typed-payload
     * round-trip reaches the client as the bound [HttpInitError] Throwable, and the
     * user-defined code 100 (no default mapping) lands on HTTP 500 with the original code
     * recoverable from the `X-Ksrpc-Error-Code` header.
     */
    @Test
    fun typedErrorRoundTripsOverHttp() = runBlockingUnit {
        val service = object : HttpTypedErrorService {
            override suspend fun init(input: String): String {
                throw HttpInitError(retry = true, reason = "input was: $input")
            }

            override suspend fun missingOnly(input: String): String = "ok"
        }
        val env = ksrpcEnvironment { }
        httpTest(
            serve = {
                serveHttp("/", service, env)
            },
            test = { port ->
                val httpClient = HttpClient()
                try {
                    val client = httpClient.asHttpChannelClient(
                        baseUrl = "http://localhost:$port/",
                        env = env
                    )
                    val stub = client.defaultChannel().toStub<HttpTypedErrorService, String>()
                    try {
                        stub.init("seed")
                        fail("Expected HttpInitError")
                    } catch (t: Throwable) {
                        assertIs<HttpInitError>(t)
                        assertEquals(true, t.retry)
                        assertEquals("input was: seed", t.reason)
                    }
                } finally {
                    httpClient.close()
                }
            },
            isWebsocket = false
        )
    }

    /**
     * Default code-to-status map: an endpoint-not-found (sentinel `-32601`) lands on HTTP
     * 404 without any header — the client recovers the code from the status alone.
     */
    @Test
    fun endpointNotFoundDefaultsTo404() = runBlockingUnit {
        val service = object : HttpTypedErrorService {
            override suspend fun init(input: String): String = "ok"
            override suspend fun missingOnly(input: String): String = "ok"
        }
        val env = ksrpcEnvironment { }
        httpTest(
            serve = {
                serveHttp("/", service, env)
            },
            test = { port ->
                val httpClient = HttpClient()
                try {
                    // Hit a method the server doesn't expose to force the not-found path.
                    val raw: HttpResponse = httpClient.post("http://localhost:$port/call/nope") {
                        headers["binary"] = "false"
                        headers["channel"] = ""
                        // Body intentionally empty.
                    }
                    assertEquals(HttpStatusCode.NotFound, raw.status)
                    // No fallback header on default-mapped statuses.
                    assertNull(raw.headers[KSRPC_ERROR_CODE_HEADER])
                } finally {
                    httpClient.close()
                }
            },
            isWebsocket = false
        )
    }

    /**
     * Custom-code fallback: a user code outside the default map lands on HTTP 500 with
     * `X-Ksrpc-Error-Code` carrying the original code so the client can recover it for
     * `@KsError`-bound payload routing. The client side here has no @KsError binding
     * for code 100 on the called endpoint (the call goes via the raw HTTP transport with
     * no service-side stub) — under the typed-Throwable contract that means the forward-
     * compat path on [com.monkopedia.ksrpc.RpcMethod.decodeError] would surface a generic
     * [KsrpcException] with the raw payload, but here we exercise the wire envelope
     * directly to verify the header carries the user code.
     */
    @Test
    fun customCodeFallsBackToHeader() = runBlockingUnit {
        val service = object : HttpTypedErrorService {
            override suspend fun init(input: String): String {
                throw KsrpcException(code = 100, message = "user failure")
            }

            override suspend fun missingOnly(input: String): String = "ok"
        }
        val env = ksrpcEnvironment { }
        httpTest(
            serve = {
                serveHttp("/", service, env)
            },
            test = { port ->
                val httpClient = HttpClient()
                try {
                    val raw: HttpResponse =
                        httpClient.post("http://localhost:$port/call/init") {
                            headers["binary"] = "false"
                            headers["channel"] = ""
                            this.headers["Content-Type"] = "text/plain"
                            // Body must be a valid JSON-encoded string for the
                            // String-typed `init` arg to deserialize cleanly; otherwise
                            // dispatch fails before reaching the handler and the response
                            // shape is the generic INTERNAL_ERROR_CODE 500, not the
                            // user-coded 100 fallback we're testing.
                            io.ktor.client.request.setBody("\"hello\"")
                        }
                    assertEquals(HttpStatusCode.InternalServerError, raw.status)
                    assertEquals("100", raw.headers[KSRPC_ERROR_CODE_HEADER])
                } finally {
                    httpClient.close()
                }
            },
            isWebsocket = false
        )
    }

    /**
     * Custom error-code-to-status map: when user maps 100 -> 401, the response status is
     * 401 (no header fallback needed), and the client's matching map decodes 401 back to
     * code 100 for typed-payload routing — yielding the bound [HttpInitError] Throwable.
     */
    @Test
    fun customErrorMappingIsRespectedRoundTrip() = runBlockingUnit {
        val service = object : HttpTypedErrorService {
            override suspend fun init(input: String): String {
                throw HttpInitError(retry = true, reason = "session expired")
            }

            override suspend fun missingOnly(input: String): String = "ok"
        }
        val env = ksrpcEnvironment { }
        val customMap = mapOf(
            KsrpcException.ENDPOINT_NOT_FOUND_CODE to 404,
            KsrpcException.INTERNAL_ERROR_CODE to 500,
            100 to 401
        )
        httpTest(
            serve = {
                serveHttp("/", service, env, customMap)
            },
            test = { port ->
                val httpClient = HttpClient()
                try {
                    val client = httpClient.asHttpChannelClient(
                        baseUrl = "http://localhost:$port/",
                        env = env,
                        errorCodeToHttpStatus = customMap
                    )
                    val stub = client.defaultChannel().toStub<HttpTypedErrorService, String>()
                    try {
                        stub.init("seed")
                        fail("Expected HttpInitError")
                    } catch (t: Throwable) {
                        assertIs<HttpInitError>(t)
                        assertEquals("session expired", t.reason)
                    }
                } finally {
                    httpClient.close()
                }
            },
            isWebsocket = false
        )
    }
}
