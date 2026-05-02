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

import com.monkopedia.ksrpc.IntrospectableRpcService
import com.monkopedia.ksrpc.annotation.KsIntrospectable
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.ksrpcEnvironment
import com.monkopedia.ksrpc.serialized
import com.monkopedia.ksrpc.toStub

// ---- Introspectable service ----

@KsService
@KsIntrospectable
interface CatalogService : IntrospectableRpcService {
    @KsMethod("/search")
    suspend fun search(query: String): String

    @KsMethod("/count")
    suspend fun count(): Int
}

// ---- Sample functions ----

/**
 * Demonstrates declaring an introspectable service with `@KsIntrospectable`.
 */
fun introspectableService() {
    // Extend IntrospectableRpcService and add @KsIntrospectable.
    // The compiler plugin generates a getIntrospection() implementation
    // that returns metadata about the service and its endpoints.
}

/**
 * Demonstrates using the introspection API to query endpoint metadata.
 */
suspend fun queryingEndpointInfo() {
    val service = object : CatalogService {
        override suspend fun search(query: String): String = "result"
        override suspend fun count(): Int = 42
    }

    val env = ksrpcEnvironment { }
    val serialized = service.serialized(env)
    val stub = serialized.toStub<CatalogService, String>()

    // Get the IntrospectionService for this service.
    val introspection = stub.getIntrospection()

    // Query the service name.
    val name = introspection.getServiceName()

    // List all endpoints.
    val endpoints = introspection.getEndpoints()

    // Get detailed info about a specific endpoint (input/output types).
    val searchInfo = introspection.getEndpointInfo("search")
    val inputType = searchInfo.input
    val outputType = searchInfo.output
}
