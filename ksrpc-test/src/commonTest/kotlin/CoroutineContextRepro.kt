/*
 * Minimal reproduction: coroutineContext element lookup returns null from
 * a method on an anonymous object called inside withContext, even though:
 *   - inline lookup in the same withContext block succeeds
 *   - top-level suspend functions see the element
 *   - methods on concrete (non-anonymous) classes see the element
 *
 * Only anonymous object methods are affected. This blocks @KsContext
 * handler-side propagation (issue #81) since RPC handlers are typically
 * anonymous object implementations of @KsService interfaces.
 *
 * Suspected Kotlin 2.3.x compiler issue with how coroutineContext is
 * resolved inside anonymous object suspend methods.
 */
package com.monkopedia.ksrpc

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.withContext

// --- Element ---

class MyToken(val value: String) : CoroutineContext.Element {
    override val key get() = Key
    companion object Key : CoroutineContext.Key<MyToken>
}

// --- Interfaces ---

interface Probe {
    suspend fun probe(input: String): String
}

// --- Concrete class ---

class ConcreteProbe : Probe {
    override suspend fun probe(input: String): String {
        return coroutineContext[MyToken]?.value ?: "null"
    }
}

// --- Top-level suspend function ---

suspend fun readMyToken(): String? = coroutineContext[MyToken]?.value

class CoroutineContextRepro {

    // PASSES: inline lookup inside withContext
    @Test
    fun inlineLookupWorks() = kotlinx.coroutines.runBlocking {
        withContext(MyToken("hello")) {
            val result = coroutineContext[MyToken]
            assertNotNull(result, "Inline lookup should find MyToken")
            assertEquals("hello", result.value)
        }
    }

    // PASSES: top-level suspend function
    @Test
    fun topLevelSuspendFnWorks() = kotlinx.coroutines.runBlocking {
        withContext(MyToken("hello")) {
            assertEquals("hello", readMyToken())
        }
    }

    // PASSES: concrete class method
    @Test
    fun concreteClassWorks() = kotlinx.coroutines.runBlocking {
        val service = ConcreteProbe()
        withContext(MyToken("hello")) {
            assertEquals("hello", service.probe("ignored"))
        }
    }

    // FAILS: anonymous object method — coroutineContext[MyToken] returns null
    @Test
    fun anonymousObjectFails() = kotlinx.coroutines.runBlocking {
        val service = object : Probe {
            override suspend fun probe(input: String): String {
                return coroutineContext[MyToken]?.value ?: "null"
            }
        }
        withContext(MyToken("hello")) {
            assertEquals("hello", service.probe("ignored"),
                "Anonymous object should see MyToken from withContext")
        }
    }
}
