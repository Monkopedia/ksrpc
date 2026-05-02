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

// ---- Sub-service interfaces ----

@KsService
interface ScopedWorker : RpcService {
    @KsMethod("/execute")
    suspend fun execute(command: String): String
}

@KsService
interface WorkerFactory : RpcService {
    @KsMethod("/create_worker")
    suspend fun createWorker(name: String): ScopedWorker
}

@KsService
interface EventConsumer : RpcService {
    @KsMethod("/on_event")
    suspend fun onEvent(event: String)
}

@KsService
interface EventProducer : RpcService {
    @KsMethod("/subscribe")
    suspend fun subscribe(consumer: EventConsumer): String
}

// ---- Sample functions ----

/**
 * Demonstrates a service method that returns a sub-service.
 */
suspend fun subServiceOutput() {
    // A @KsMethod can return another @KsService type. The returned service
    // is hosted as a sub-service on the same connection, with its own
    // channel id managed by the framework.
    val factory = object : WorkerFactory {
        override suspend fun createWorker(name: String): ScopedWorker =
            object : ScopedWorker {
                override suspend fun execute(command: String): String =
                    "[$name] executed: $command"
            }
    }

    // Serialize and use through a stub -- sub-services work transparently.
    val env = ksrpcEnvironment { }
    val serialized = factory.serialized(env)
    val stub = serialized.toStub<WorkerFactory, String>()
    val worker = stub.createWorker("build")
    val result = worker.execute("compile")
}

/**
 * Demonstrates a service method that accepts a sub-service as a callback.
 */
suspend fun subServiceInput() {
    // A @KsMethod can accept another @KsService type as input. The caller
    // provides a service implementation that the server calls back into.
    val producer = object : EventProducer {
        override suspend fun subscribe(consumer: EventConsumer): String {
            // The server calls back into the client-provided consumer.
            consumer.onEvent("connected")
            consumer.onEvent("data-ready")
            return "subscription-123"
        }
    }

    val env = ksrpcEnvironment { }
    val serialized = producer.serialized(env)
    val stub = serialized.toStub<EventProducer, String>()

    // The client provides a callback implementation.
    val callback = object : EventConsumer {
        override suspend fun onEvent(event: String) {
            println("Received event: $event")
        }
    }
    val subscriptionId = stub.subscribe(callback)
}
