# Kotlin Simple RPCs

[![GitHub license](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat)](http://www.apache.org/licenses/LICENSE-2.0)
[![Kotlin](https://img.shields.io/badge/kotlin-2.3.20-blue.svg?logo=kotlin)](http://kotlinlang.org)
[![Maven Central](https://img.shields.io/maven-central/v/com.monkopedia.ksrpc/ksrpc-core/0.11.1)](https://search.maven.org/artifact/com.monkopedia.ksrpc/ksrpc-core/0.11.1/pom)
[![KDoc link](https://img.shields.io/badge/API_reference-KDoc-blue)](https://monkopedia.github.io/ksrpc/)

Define a service interface once in Kotlin common, then call it over HTTP, sockets, stdin/out,
websockets, or in-process, without changing your service code. Built for Kotlin Multiplatform,
curl-testable by default, and LSP-protocol-compatible out of the box.

## Quick Start

```kotlin
@KsService
interface GreetingService : RpcService {
    @KsMethod("/greet")
    suspend fun greet(name: String): String
}

// Implement once
class GreetingServiceImpl : GreetingService {
    override suspend fun greet(name: String) = "Hello, $name!"
}

// Host over HTTP
val env = ksrpcEnvironment { }
embeddedServer(Netty, 8080) {
    routing { serveHttp("/api", GreetingServiceImpl(), env) }
}.start()

// Call from any platform
val service = HttpClient { }.asHttpChannelClient("http://localhost:8080/api", env)
    .defaultChannel().toStub<GreetingService>()
println(service.greet("world")) // "Hello, world!"
```

## Build Setup

```kotlin
plugins {
    id("com.monkopedia.ksrpc.plugin") version "0.11.1"
}

dependencies {
    implementation("com.monkopedia.ksrpc:ksrpc-core:0.11.1")
    // Add transport modules as needed:
    // implementation("com.monkopedia.ksrpc:ksrpc-ktor-server:0.11.1")
    // implementation("com.monkopedia.ksrpc:ksrpc-ktor-client:0.11.1")
}
```

## Supported Transports

| Transport | Platforms | Module |
|-----------|-----------|--------|
| HTTP | JVM, Native, JS/WASM (client) | `ksrpc-ktor-client`, `ksrpc-ktor-server` |
| WebSockets | JVM, Native, JS/WASM (client) | `ksrpc-ktor-websocket-client`, `ksrpc-ktor-websocket-server` |
| Sockets | JVM, POSIX Native | `ksrpc-sockets` |
| Stdin/out | JVM, POSIX Native | `ksrpc-sockets` |
| JSON-RPC 2.0 | JVM, POSIX Native | `ksrpc-jsonrpc` |
| Service Workers | JS (experimental) | `ksrpc-service-worker` |

## Features

- **Service declaration** -- Define services with [`@KsService`](https://monkopedia.github.io/ksrpc/ksrpc-api/com.monkopedia.ksrpc.annotation/-ks-service/index.html) and [`@KsMethod`](https://monkopedia.github.io/ksrpc/ksrpc-api/com.monkopedia.ksrpc.annotation/-ks-method/index.html); the compiler plugin generates stubs and companions automatically. [Guide](https://monkopedia.github.io/ksrpc/)
- **Sub-services** -- Pass services as method parameters or return values for contextual callbacks and hierarchical APIs. [Guide](https://monkopedia.github.io/ksrpc/)
- **Bidirectional communication** -- [`Connection`](https://monkopedia.github.io/ksrpc/ksrpc-core/com.monkopedia.ksrpc.channels/-connection/index.html) supports hosting and calling services simultaneously over the same channel. [Guide](https://monkopedia.github.io/ksrpc/)
- **Binary data** -- Stream binary payloads via [`RpcBinaryData`](https://monkopedia.github.io/ksrpc/ksrpc-core/com.monkopedia.ksrpc.channels/-rpc-binary-data/index.html) with adapters for ktor, kotlinx-io, and okio.
- **Typed errors** -- Map exceptions to wire-format error codes with [`@KsError`](https://monkopedia.github.io/ksrpc/ksrpc-api/com.monkopedia.ksrpc.annotation/-ks-error/index.html) and [`KsrpcException`](https://monkopedia.github.io/ksrpc/ksrpc-core/com.monkopedia.ksrpc/-ksrpc-exception/index.html). [Guide](https://monkopedia.github.io/ksrpc/)
- **Context propagation** -- Propagate per-call context (auth tokens, trace IDs) across transports with [`@KsContext`](https://monkopedia.github.io/ksrpc/ksrpc-api/com.monkopedia.ksrpc.annotation/-ks-context/index.html). [Guide](https://monkopedia.github.io/ksrpc/)
- **Introspection** -- Opt in with [`@KsIntrospectable`](https://monkopedia.github.io/ksrpc/ksrpc-introspection/com.monkopedia.ksrpc.annotation/-ks-introspectable/index.html) to expose endpoint metadata and schemas at runtime. [Guide](https://monkopedia.github.io/ksrpc/)
- **Flow streaming** -- Use `Flow<T>` in method signatures for streaming results over any transport, backed by [`KsFlowService`](https://monkopedia.github.io/ksrpc/ksrpc-flow/com.monkopedia.ksrpc.flow/-ks-flow-service/index.html). [Guide](https://monkopedia.github.io/ksrpc/)
- **JSON-RPC 2.0 notifications** -- Mark methods with [`@KsNotification`](https://monkopedia.github.io/ksrpc/ksrpc-api/com.monkopedia.ksrpc.annotation/-ks-notification/index.html) for fire-and-forget semantics.

## Why not gRPC?

ksrpc exists because gRPC and protobuf didn't quite fit. I wanted JSON on the wire so I could
curl at services for testing, stdin/out support like LSP, and first-class Kotlin Multiplatform
coverage. ksrpc fills that niche: simple service declarations, multiple transports, and
broad platform support.

## Documentation

Full API reference and guides are hosted at [monkopedia.github.io/ksrpc](https://monkopedia.github.io/ksrpc/).

## License

```
Copyright 2026 Jason Monk

Licensed under the Apache License, Version 2.0
```
