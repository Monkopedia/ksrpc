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

import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.annotation.KsrpcInternal
import com.monkopedia.ksrpc.jsonrpc.internal.JsonRpcTransformer
import com.monkopedia.ksrpc.jsonrpc.internal.JsonRpcWriterBase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Coverage for issue #170 — JSON-RPC 2.0 §4 explicitly allows `params` to be omitted
 * from a Request object. Real LSP clients (lsp4j, vscode) routinely send 0-arg calls
 * like `shutdown` and notifications like `exit` with no `params` field at all.
 *
 * The server-side dispatcher must accept these requests when the resolved method's
 * input type is `Unit` (the synthesized input for 0-arg `@KsMethod` functions per
 * #40). Methods that require real params still surface a deserialization error, but
 * not the cryptic "Expected start of the object '{', but had 'n' instead" produced
 * when the literal string "null" is fed to an object deserializer.
 */
@KsService
interface ShutdownService : RpcService {
    @KsMethod("shutdown")
    suspend fun shutdown(): String

    @KsMethod("exit")
    suspend fun exit()

    @KsMethod("greet")
    suspend fun greet(name: String): String
}

private class ShutdownServiceImpl(val onExit: () -> Unit = {}) : ShutdownService {
    override suspend fun shutdown(): String = "ok"
    override suspend fun exit() {
        onExit()
    }
    override suspend fun greet(name: String): String = "hello $name"
}

class JsonRpcOmittedParamsTest {

    @Test
    fun zeroArgMethodAcceptsRequestWithNoParamsField() = runBlockingUnit {
        val transformer = ScriptedTransformer()
        val env = ksrpcEnvironment { }
        val writer = JsonRpcWriterBase(
            scope = CoroutineScope(coroutineContext + SupervisorJob()),
            context = coroutineContext,
            env = env,
            comm = transformer
        )
        try {
            val service: ShutdownService = ShutdownServiceImpl()
            writer.registerDefault(service.serialized<ShutdownService, String>(env))

            // No `params` field at all — what lsp4j sends for shutdown.
            transformer.inject("""{"jsonrpc":"2.0","id":1,"method":"shutdown"}""")

            val response = withTimeout(2_000) { transformer.nextOutgoing() }
            val obj = response.jsonObject
            assertEquals(JsonPrimitive(1), obj["id"])
            assertNull(obj["error"])
            assertEquals(JsonPrimitive("ok"), obj["result"])
        } finally {
            writer.close()
        }
    }

    @Test
    fun zeroArgMethodStillAcceptsExistingEmptyObjectParams() = runBlockingUnit {
        // Regression check — ksrpc-on-both-ends always sent `params: {}` and that
        // path must continue to work after the fix.
        val transformer = ScriptedTransformer()
        val env = ksrpcEnvironment { }
        val writer = JsonRpcWriterBase(
            scope = CoroutineScope(coroutineContext + SupervisorJob()),
            context = coroutineContext,
            env = env,
            comm = transformer
        )
        try {
            val service: ShutdownService = ShutdownServiceImpl()
            writer.registerDefault(service.serialized<ShutdownService, String>(env))

            transformer.inject("""{"jsonrpc":"2.0","id":3,"method":"shutdown","params":{}}""")

            val response = withTimeout(2_000) { transformer.nextOutgoing() }
            val obj = response.jsonObject
            assertEquals(JsonPrimitive(3), obj["id"])
            assertNull(obj["error"])
            assertEquals(JsonPrimitive("ok"), obj["result"])
        } finally {
            writer.close()
        }
    }

    @Test
    fun zeroArgNotificationAcceptsRequestWithNoParamsField() = runBlockingUnit {
        val transformer = ScriptedTransformer()
        val env = ksrpcEnvironment { }
        val writer = JsonRpcWriterBase(
            scope = CoroutineScope(coroutineContext + SupervisorJob()),
            context = coroutineContext,
            env = env,
            comm = transformer
        )
        val exitCalled = CompletableDeferred<Unit>()
        try {
            val service: ShutdownService = ShutdownServiceImpl(
                onExit = { exitCalled.complete(Unit) }
            )
            writer.registerDefault(service.serialized<ShutdownService, String>(env))

            // Notification: no `id`, no `params` — what lsp4j sends for exit.
            transformer.inject("""{"jsonrpc":"2.0","method":"exit"}""")

            // The handler must run; the test will fail by timeout if the dispatcher
            // rejected the request before reaching the service.
            withTimeout(2_000) { exitCalled.await() }

            // Notifications produce no response — give the writer a moment and assert
            // nothing came back on the outgoing channel.
            assertNull(transformer.tryNextOutgoing())
        } finally {
            writer.close()
        }
    }

    @Test
    fun methodRequiringParamsStillErrorsWhenParamsOmitted() = runBlockingUnit {
        // Methods whose input is NOT Unit must still fail when params are missing —
        // they just fail with a sensible deserialization error rather than the
        // cryptic 'n' parser error. The handler is NOT invoked.
        val transformer = ScriptedTransformer()
        val env = ksrpcEnvironment { }
        val writer = JsonRpcWriterBase(
            scope = CoroutineScope(coroutineContext + SupervisorJob()),
            context = coroutineContext,
            env = env,
            comm = transformer
        )
        try {
            val service: ShutdownService = ShutdownServiceImpl()
            writer.registerDefault(service.serialized<ShutdownService, String>(env))

            transformer.inject("""{"jsonrpc":"2.0","id":4,"method":"greet"}""")

            val response = withTimeout(2_000) { transformer.nextOutgoing() }
            val obj = response.jsonObject
            assertEquals(JsonPrimitive(4), obj["id"])
            val error = obj["error"]
            assertNotNull(error, "Expected an error response for method requiring params")
            // The error message should NOT be the legacy 'expected { but had n'
            // confused-deserializer message — that was the bug.
            val msg = error.jsonObject["message"]?.jsonPrimitive?.content ?: ""
            assertTrue(
                "had 'n'" !in msg,
                "Error message should not be the legacy null-fed-to-object error: $msg"
            )
        } finally {
            writer.close()
        }
    }

    /**
     * Test [JsonRpcTransformer] that lets the test inject raw inbound JSON-RPC frames
     * and read back outbound frames. The writer's receive loop pulls each injected
     * frame in order; outbound responses are buffered for the test to inspect.
     */
    private class ScriptedTransformer : JsonRpcTransformer() {
        private val inbound = Channel<JsonElement>(capacity = Channel.UNLIMITED)
        private val outbound = Channel<JsonElement>(capacity = Channel.UNLIMITED)
        private var closed = false

        override val isOpen: Boolean
            get() = !closed

        fun inject(rawJson: String) {
            inbound.trySend(Json.parseToJsonElement(rawJson))
        }

        suspend fun nextOutgoing(): JsonElement = outbound.receive()

        fun tryNextOutgoing(): JsonElement? = outbound.tryReceive().getOrNull()

        override suspend fun send(message: JsonElement) {
            outbound.send(message)
        }

        override suspend fun receive(): JsonElement? {
            if (closed) return null
            return inbound.receive()
        }

        override fun close(cause: Throwable?) {
            closed = true
            inbound.close(cause)
            outbound.close(cause)
        }
    }
}
