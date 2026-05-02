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
@file:Suppress("unused", "UNUSED_VARIABLE")

package com.monkopedia.ksrpc.samples

import com.monkopedia.ksrpc.KsContextBinding
import com.monkopedia.ksrpc.RpcService
import com.monkopedia.ksrpc.annotation.KsContext
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.ksrpcEnvironment
import com.monkopedia.ksrpc.serialized
import com.monkopedia.ksrpc.toStub
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.withContext

// ---- Context element + binding ----

/**
 * A coroutine context element carrying a request ID for tracing.
 * The companion object doubles as its KsContextBinding.
 */
class RequestId(val value: String) : CoroutineContext.Element {
    override val key get() = Key

    companion object Key : KsContextBinding<RequestId> {
        override val wireKey: String = "x-request-id"
        override fun toWire(value: RequestId): String = value.value
        override fun fromWire(encoded: String): RequestId = RequestId(encoded)
    }
}

/**
 * A coroutine context element carrying an authorization token.
 */
class AuthorizationToken(val bearer: String) : CoroutineContext.Element {
    override val key get() = Key

    companion object Key : KsContextBinding<AuthorizationToken> {
        override val wireKey: String = "authorization"
        override fun toWire(value: AuthorizationToken): String = value.bearer
        override fun fromWire(encoded: String): AuthorizationToken =
            AuthorizationToken(encoded)
    }
}

// ---- Meta-annotations using @KsContext ----

@KsContext(binding = RequestId.Key::class)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class WithRequestId

@KsContext(binding = AuthorizationToken.Key::class)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class Authorized

// ---- Service using context annotations ----

@Authorized
@KsService
interface SecureService : RpcService {
    @KsMethod("/whoami")
    @WithRequestId
    suspend fun whoAmI(input: String): String
}

// ---- Sample functions ----

/**
 * Demonstrates creating a KsContextBinding implementation.
 */
fun contextBindingDefinition() {
    // A KsContextBinding pairs a CoroutineContext.Element with a wire key.
    // Declare the binding as a named companion on the element type.
    val wireKey = RequestId.Key.wireKey // "x-request-id"

    // Round-trip: encode to wire, decode from wire
    val original = RequestId("abc-123")
    val encoded = RequestId.Key.toWire(original)
    val decoded = RequestId.Key.fromWire(encoded)
}

/**
 * Demonstrates defining @KsContext meta-annotations.
 */
fun contextAnnotationUsage() {
    // @KsContext is a meta-annotation applied to your own annotation.
    // Apply the annotation at the service level (all methods) or per-method.
    //
    // @Authorized              -- applied to SecureService (all methods get it)
    // @WithRequestId           -- applied to whoAmI only
    //
    // The compiler plugin validates that bindings implement KsContextBinding
    // and that no two bindings share the same wireKey on a single method.
}

/**
 * Demonstrates using withContext to propagate values across the wire.
 */
suspend fun contextPropagationUsage() {
    // Server handler reads context from the coroutine context.
    val service = object : SecureService {
        override suspend fun whoAmI(input: String): String {
            val auth = coroutineContext[AuthorizationToken.Key]
            val reqId = coroutineContext[RequestId.Key]
            return "auth=${auth?.bearer}, reqId=${reqId?.value}"
        }
    }

    val env = ksrpcEnvironment { }
    val serialized = service.serialized(env)
    val stub = serialized.toStub<SecureService, String>()

    // Client installs context elements using standard withContext.
    // The ksrpc runtime propagates bound elements across the wire.
    withContext(AuthorizationToken("secret-token") + RequestId("req-42")) {
        val result = stub.whoAmI("hello")
    }
}
