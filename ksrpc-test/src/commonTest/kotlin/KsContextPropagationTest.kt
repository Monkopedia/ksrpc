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

import com.monkopedia.ksrpc.annotation.KsContext
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.annotation.KsrpcInternal
import com.monkopedia.ksrpc.internal.HostSerializedChannelImpl
import com.monkopedia.ksrpc.internal.asClient
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.withContext

// --- Context element + binding ---

class AuthToken(val token: String) : CoroutineContext.Element {
    override val key get() = Key

    companion object Key : KsContextBinding<AuthToken> {
        override val wireKey: String = "x-auth-token"
        override fun toWire(value: AuthToken): String = value.token
        override fun fromWire(encoded: String): AuthToken = AuthToken(encoded)
    }
}

class TraceId(val value: String) : CoroutineContext.Element {
    override val key get() = Key

    companion object Key : KsContextBinding<TraceId> {
        override val wireKey: String = "x-trace-id"
        override fun toWire(value: TraceId): String = value.value
        override fun fromWire(encoded: String): TraceId = TraceId(encoded)
    }
}

// --- Annotations ---

@KsContext(binding = AuthToken.Key::class)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class Auth

@KsContext(binding = TraceId.Key::class)
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class WithTrace

// --- Service ---

@Auth
@KsService
interface ContextService : RpcService {
    @KsMethod("/greet")
    @WithTrace
    suspend fun greet(input: String): String

    @KsMethod("/auth_only")
    suspend fun authOnly(input: String): String
}

// --- Concrete handler implementations (NOT anonymous objects) ---
// Anonymous objects declared inside a CoroutineScope capture the scope's
// coroutineContext instead of reading from the suspend continuation. Using
// concrete top-level classes avoids this Kotlin limitation.

class GreetHandler : ContextService {
    override suspend fun greet(input: String): String {
        val auth = coroutineContext[AuthToken.Key]
            ?: return "no-auth"
        val trace = coroutineContext[TraceId.Key]
            ?: return "auth=${auth.token},no-trace"
        return "auth=${auth.token},trace=${trace.value}"
    }

    override suspend fun authOnly(input: String): String {
        val auth = coroutineContext[AuthToken.Key]
            ?: return "no-auth"
        return "auth=${auth.token}"
    }
}

class AuthOnlyHandler : ContextService {
    override suspend fun greet(input: String): String = error("unused")
    override suspend fun authOnly(input: String): String {
        val auth = coroutineContext[AuthToken.Key]
        val trace = coroutineContext[TraceId.Key]
        return "auth=${auth?.token},trace=${trace?.value}"
    }
}

/**
 * Tests for #81 — compile-time verification and runtime propagation for
 * `@KsContext` context bindings.
 *
 * Note: handler-side context lookup uses `coroutineContext[AuthToken.Key]`
 * (the named companion), not `coroutineContext[AuthToken]` (the class name).
 * With a named companion pattern, Kotlin resolves the bare class name to
 * the type, not to the companion object.
 */
class KsContextPropagationTest {

    @Test
    fun contextBindingsPopulated() {
        val rpcObject = rpcObject<ContextService>()
        val greetMethod = rpcObject.findEndpoint("greet")
        val bindings = greetMethod.contextBindings
        assertEquals(
            2,
            bindings.size,
            "Expected 2 context bindings on /greet, got ${bindings.map { it.binding.wireKey }}"
        )
        val wireKeys = bindings.map { it.binding.wireKey }.toSet()
        assertTrue("x-auth-token" in wireKeys, "Missing x-auth-token; got $wireKeys")
        assertTrue("x-trace-id" in wireKeys, "Missing x-trace-id; got $wireKeys")

        val authOnlyMethod = rpcObject.findEndpoint("auth_only")
        val authOnlyBindings = authOnlyMethod.contextBindings
        assertEquals(
            1,
            authOnlyBindings.size,
            "Expected 1 context binding on /auth_only, got " +
                authOnlyBindings.map { it.binding.wireKey }
        )
        assertEquals("x-auth-token", authOnlyBindings.single().binding.wireKey)
    }

    @Test
    fun contextPropagatesInProcess() = runBlockingUnit {
        withInProcessChannel(GreetHandler()) { stub ->
            withContext(AuthToken("secret-123") + TraceId("abc-def")) {
                val result = stub.greet("hello")
                assertEquals("auth=secret-123,trace=abc-def", result)
            }
        }
    }

    @Test
    fun absentContextSurfacesAsNull() = runBlockingUnit {
        withInProcessChannel(GreetHandler()) { stub ->
            // No context installed — handler should see nulls
            val result = stub.greet("hello")
            assertEquals("no-auth", result)
        }
    }

    @Test
    fun partialContextPropagates() = runBlockingUnit {
        withInProcessChannel(GreetHandler()) { stub ->
            withContext(AuthToken("only-auth")) {
                val result = stub.greet("hello")
                assertEquals("auth=only-auth,no-trace", result)
            }
        }
    }

    @Test
    fun authOnlyMethodStillSeesUnboundContext() = runBlockingUnit {
        // In-process: the caller's full coroutineContext propagates through,
        // including elements not bound on the method. Wire-level filtering
        // (only encoding bound elements) is a transport concern tested by the
        // pipe test below.
        withInProcessChannel(AuthOnlyHandler()) { stub ->
            withContext(AuthToken("token-1") + TraceId("unbound-but-visible")) {
                val result = stub.authOnly("hello")
                assertEquals("auth=token-1,trace=unbound-but-visible", result)
            }
        }
    }

    @Test
    fun authTokenElementRoundTrips() {
        val original = AuthToken("secret")
        val wire = AuthToken.Key.toWire(original)
        assertEquals("secret", wire)
        val decoded = AuthToken.Key.fromWire(wire)
        assertEquals("secret", decoded.token)
    }

    private suspend fun withInProcessChannel(
        service: ContextService,
        body: suspend (ContextService) -> Unit
    ) {
        val env = ksrpcEnvironment { }
        val channel = HostSerializedChannelImpl(env)
        try {
            channel.registerDefault(service.serialized(env))
            val stub = channel.asClient.defaultChannel().toStub<ContextService, String>()
            body(stub)
        } finally {
            try {
                channel.close()
            } catch (_: Throwable) {
            }
        }
    }
}

/**
 * Round-trip pipe-transport coverage for context propagation: exercises the full
 * encode / wire / decode flow over a real socket pipe so we verify the context
 * map survives packet serialization. The Packet wire grew an optional `cx` field.
 */
class KsContextPropagationPipeTest :
    RpcFunctionalityTest(
        supportedTypes = listOf(
            TestType.PIPE,
            TestType.SERIALIZE
        ),
        serializedChannel = {
            GreetHandler().serialized(ksrpcEnvironment { })
        },
        verifyOnChannel = { channel ->
            val stub = channel.toStub<ContextService, String>()
            withContext(AuthToken("pipe-token") + TraceId("pipe-trace")) {
                val result = stub.greet("hello")
                assertEquals("auth=pipe-token,trace=pipe-trace", result)
            }
        }
    )
