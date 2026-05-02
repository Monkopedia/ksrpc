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
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.ksrpcEnvironment
import com.monkopedia.ksrpc.rpcObject
import com.monkopedia.ksrpc.serialized
import com.monkopedia.ksrpc.toStub
import kotlinx.serialization.Serializable

// ---- Service declarations used by samples ----

@KsService
interface GreetingService : RpcService {
    @KsMethod("/greet")
    suspend fun greet(name: String): String
}

@Serializable
data class UserRequest(val name: String, val age: Int)

@Serializable
data class UserResponse(val id: Long, val greeting: String)

@KsService
interface UserService : RpcService {
    @KsMethod("/create")
    suspend fun createUser(request: UserRequest): UserResponse
}

@KsService
interface PingService : RpcService {
    @KsMethod("/ping")
    suspend fun ping(): String

    @KsMethod("/notify")
    suspend fun notify(message: String)
}

// ---- Sample functions (referenced via @sample in KDoc) ----

/**
 * Demonstrates declaring a basic ksrpc service with `@KsService` and `@KsMethod`.
 */
fun basicServiceDeclaration() {
    // Define a service interface with @KsService and @KsMethod annotations.
    // The compiler plugin generates a companion RpcObject and stub automatically.
    val rpcObj = rpcObject<GreetingService>()
    val endpoint = rpcObj.findEndpoint("greet")
}

/**
 * Demonstrates implementing a service and converting it to a serialized form.
 */
suspend fun implementAndSerialize() {
    // Implement the service interface
    val service = object : GreetingService {
        override suspend fun greet(name: String): String = "Hello, $name!"
    }

    // Serialize for hosting on any transport
    val env = ksrpcEnvironment { }
    val serialized = service.serialized(env)
}

/**
 * Demonstrates using `@Serializable` data classes as method parameters.
 */
fun serializableTypes() {
    // Any @Serializable class can be used as input or output to @KsMethod.
    // The compiler plugin handles serializer plumbing automatically.
    val rpcObj = rpcObject<UserService>()
    val endpoint = rpcObj.findEndpoint("create")
}

/**
 * Demonstrates methods with Unit input (no-arg) and Unit output (void return).
 */
fun unitInputOutput() {
    // Methods can omit the parameter (returns Unit) or the return type.
    // suspend fun ping(): String        — no input
    // suspend fun notify(message: String) — no output (returns Unit)
    val rpcObj = rpcObject<PingService>()
}
