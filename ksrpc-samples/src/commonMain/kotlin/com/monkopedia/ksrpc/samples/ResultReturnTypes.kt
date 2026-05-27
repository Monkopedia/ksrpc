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
import com.monkopedia.ksrpc.serialized
import com.monkopedia.ksrpc.toStub
import kotlinx.serialization.Serializable

// ---- Error type ----

@Serializable
data class ParseError(val input: String) : RuntimeException("Could not parse: $input")

// ---- Service with a Result<O> return type ----

@KsService
interface ParseService : RpcService {
    @KsMethod("/parse")
    @KsError(code = 200, type = ParseError::class)
    suspend fun parse(input: String): Result<Int>
}

// ---- Sample functions ----

/**
 * Demonstrates a `@KsMethod` whose return type is `Result<O>`.
 */
fun resultReturnTypeDeclaration() {
    // A Result<O> method is equivalent to a plain O method wrapped in
    // runCatching-except-cancellation. The wire format is unchanged: success
    // serializes the inner O, and failure uses the same @KsError / error
    // envelope as a thrown exception. kotlin.Result is never serialized.
    val service = object : ParseService {
        override suspend fun parse(input: String): Result<Int> =
            input.toIntOrNull()?.let { Result.success(it) }
                ?: Result.failure(ParseError(input))
    }
}

/**
 * Demonstrates calling a `Result<O>` method and handling the outcome with
 * `onSuccess` / `onFailure` instead of try/catch.
 */
suspend fun consumingResultReturnType() {
    val service = object : ParseService {
        override suspend fun parse(input: String): Result<Int> =
            input.toIntOrNull()?.let { Result.success(it) }
                ?: Result.failure(ParseError(input))
    }

    val env = ksrpcEnvironment { }
    val serialized = service.serialized(env)
    val stub = serialized.toStub<ParseService, String>()

    // The stub returns a Result and does NOT throw on failure. A failure
    // mapped via @KsError round-trips as Result.failure(typedError).
    stub.parse("42")
        .onSuccess { value -> println("Parsed $value") }
        .onFailure { error -> println("Failed: ${error.message}") }

    val outcome: Result<Int> = stub.parse("not-a-number")
    // outcome.isFailure == true; outcome.exceptionOrNull() is a ParseError.
}
