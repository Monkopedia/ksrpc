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

import com.monkopedia.ksrpc.RpcService
import com.monkopedia.ksrpc.annotation.KsError
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.ksrpcEnvironment
import com.monkopedia.ksrpc.rpcObject
import com.monkopedia.ksrpc.serialized
import com.monkopedia.ksrpc.toStub
import kotlinx.serialization.Serializable

// ---- Error types ----

@Serializable
data class ValidationError(val field: String, val reason: String) :
    RuntimeException("Validation failed on $field: $reason")

@Serializable
data class NotFoundError(val resourceId: String) :
    RuntimeException("Resource not found: $resourceId")

// ---- Service with @KsError bindings ----

@KsService
interface DocumentService : RpcService {
    @KsMethod("/create")
    @KsError(code = 100, type = ValidationError::class)
    suspend fun createDocument(content: String): String

    @KsMethod("/get")
    @KsError(code = 100, type = ValidationError::class)
    @KsError(code = 101, type = NotFoundError::class)
    suspend fun getDocument(id: String): String
}

// ---- Sample functions ----

/**
 * Demonstrates defining typed error mappings with `@KsError`.
 */
fun errorAnnotationUsage() {
    // @KsError binds a @Serializable Throwable subclass to an integer error code.
    // Multiple @KsError annotations can appear on the same method.
    // The compiler plugin captures the bidirectional code <-> serializer map.
    val rpcObj = rpcObject<DocumentService>()
    val createEndpoint = rpcObj.findEndpoint("create")
    val getEndpoint = rpcObj.findEndpoint("get")
}

/**
 * Demonstrates throwing and catching typed errors across the wire.
 */
suspend fun throwingTypedErrors() {
    // Server implementation throws typed errors directly.
    val service = object : DocumentService {
        override suspend fun createDocument(content: String): String {
            if (content.isBlank()) {
                throw ValidationError("content", "must not be blank")
            }
            return "doc-001"
        }

        override suspend fun getDocument(id: String): String {
            if (!id.startsWith("doc-")) {
                throw ValidationError("id", "invalid format")
            }
            throw NotFoundError(id)
        }
    }

    val env = ksrpcEnvironment { }
    val serialized = service.serialized(env)
    val stub = serialized.toStub<DocumentService, String>()

    // Clients catch the original typed exception after deserialization.
    try {
        stub.getDocument("doc-missing")
    } catch (e: NotFoundError) {
        // e.resourceId == "doc-missing"
        println("Not found: ${e.resourceId}")
    } catch (e: ValidationError) {
        println("Validation: ${e.field} - ${e.reason}")
    }
}
