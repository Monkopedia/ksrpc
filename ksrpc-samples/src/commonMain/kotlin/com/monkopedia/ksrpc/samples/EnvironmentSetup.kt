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

import com.monkopedia.ksrpc.ErrorListener
import com.monkopedia.ksrpc.ksrpcEnvironment
import com.monkopedia.ksrpc.reconfigure
import kotlinx.serialization.json.Json

/**
 * Demonstrates basic ksrpcEnvironment builder usage.
 */
fun environmentBasicSetup() {
    // Create an environment with default settings (JSON serialization).
    val env = ksrpcEnvironment { }

    // Create an environment with a custom Json configuration.
    val customEnv = ksrpcEnvironment(
        Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }
    ) { }
}

/**
 * Demonstrates using the error listener in a ksrpc environment.
 */
fun environmentWithErrorListener() {
    // Set an error listener to handle transport-level errors.
    val env = ksrpcEnvironment {
        errorListener = ErrorListener { throwable ->
            println("ksrpc error: ${throwable.message}")
        }
    }

    // Reconfigure an existing environment with a new error listener.
    val reconfigured = env.reconfigure {
        errorListener = ErrorListener { throwable ->
            println("Reconfigured handler: ${throwable.message}")
        }
    }
}
