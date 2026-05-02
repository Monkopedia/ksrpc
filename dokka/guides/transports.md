# Module transports

# Transports

## Overview

ksrpc supports multiple transports. Choose based on your platform targets, whether you need bidirectional communication, and protocol requirements.

| Transport | Module | Platforms | Bidirectional | Sub-services | Binary |
|-----------|--------|-----------|---------------|--------------|--------|
| HTTP | `ksrpc-ktor-client` / `ksrpc-ktor-server` | JVM, Native, JS/WASM (client) | No | Output only | Yes (streaming) |
| WebSocket | `ksrpc-ktor-websocket-client` / `ksrpc-ktor-websocket-server` | JVM, Native, JS/WASM (client) | Yes | Yes | Yes (streaming) |
| Sockets | `ksrpc-sockets` | JVM, POSIX Native | Yes | Yes | Yes (buffered) |
| Stdin/out | `ksrpc-sockets` | JVM, POSIX Native | Yes | Yes | Yes (buffered) |
| JSON-RPC 2.0 | `ksrpc-jsonrpc` | JVM, POSIX Native | Yes | No | No |
| Service Worker | `ksrpc-service-worker` | JS, WASM (experimental) | Yes | Yes | Yes |

All transports use JSON as the default wire format via `kotlinx.serialization`.

## HTTP

HTTP is request/response only. Each `@KsMethod` call is a POST to a sub-path of the base URL. Returns a `ChannelClient`, not a `Connection`.

### Server (ktor)

```kotlin
import com.monkopedia.ksrpc.ksrpcEnvironment
import com.monkopedia.ksrpc.ktor.serveHttp
import io.ktor.server.routing.routing

val env = ksrpcEnvironment { }
embeddedServer(Netty, port = 8080) {
    routing {
        serveHttp("/api/myservice", MyServiceImpl(), env)
    }
}.start(wait = true)
```

`serveHttp` also accepts a pre-serialized `SerializedService<String>` or a `SerializedChannel<String>`.

### Client

```kotlin
import com.monkopedia.ksrpc.ktor.asHttpChannelClient
import com.monkopedia.ksrpc.toStub

val env = ksrpcEnvironment { }
val channelClient = HttpClient { }
    .asHttpChannelClient("http://localhost:8080/api/myservice", env)
val service = channelClient.defaultChannel().toStub<MyService>()
```

### Error mapping

HTTP maps ksrpc error codes to HTTP status codes. The default mapping sends `ENDPOINT_NOT_FOUND_CODE` (-32601) to 404 and `INTERNAL_ERROR_CODE` (-32603) to 500. Custom `@KsError` codes default to 500 with the original code in an `X-Ksrpc-Error-Code` header. You can pass a custom map to both `serveHttp` and `asHttpChannelClient`:

```kotlin
val errorMap = mapOf(
    100 to 422, // map your custom error code to an HTTP status
    KsrpcException.ENDPOINT_NOT_FOUND_CODE to 404,
    KsrpcException.INTERNAL_ERROR_CODE to 500
)
serveHttp("/api/myservice", service, env, errorCodeToHttpStatus = errorMap)
```

## WebSocket

WebSocket connections are bidirectional and support sub-services in both directions.

### Server

```kotlin
import com.monkopedia.ksrpc.ktor.websocket.serveWebsocket

embeddedServer(Netty, port = 8080) {
    install(WebSockets)
    routing {
        serveWebsocket("/ws/myservice", MyServiceImpl(), env)
    }
}.start(wait = true)
```

### Client

```kotlin
import com.monkopedia.ksrpc.ktor.websocket.asWebsocketConnection

val env = ksrpcEnvironment { }
val client = HttpClient { install(WebSockets) }
val connection = client.asWebsocketConnection("ws://localhost:8080/ws/myservice", env)
val service = connection.defaultChannel().toStub<MyService>()
```

The returned `Connection<String>` supports `registerDefault` for hosting a service back to the server. See the [bidirectional guide](bidirectional.md).

## Sockets

The socket transport uses a content-length-prefixed protocol over raw byte streams. Supports bidirectional communication with full sub-service support.

### Server

```kotlin
import com.monkopedia.ksrpc.sockets.asConnection
import com.monkopedia.ksrpc.channels.registerDefault

val env = ksrpcEnvironment { }
val serverSocket = ServerSocket(1234)

while (true) {
    val socket = serverSocket.accept()
    launch {
        val connection = (socket.getInputStream() to socket.getOutputStream())
            .asConnection(env)
        connection.registerDefault(MyServiceImpl())
    }
}
```

### Client

```kotlin
val socket = Socket("localhost", 1234)
val connection = (socket.getInputStream() to socket.getOutputStream())
    .asConnection(env)
val service = connection.defaultChannel().toStub<MyService>()
```

On Kotlin/Native, use `ByteReadChannel` / `ByteWriteChannel` pairs instead of JVM streams.

## Stdin/out

A convenience for the socket protocol over standard I/O streams. Useful for LSP-style subprocess communication.

```kotlin
import com.monkopedia.ksrpc.sockets.withStdInOut

val env = ksrpcEnvironment { }
withStdInOut(env) { connection ->
    connection.registerDefault(MyServiceImpl())
}
```

On JVM, you can also launch a subprocess and connect to it:

```kotlin
val connection = ProcessBuilder("my-service-binary")
    .asConnection(env)
val service = connection.defaultChannel().toStub<MyService>()
```

## JSON-RPC 2.0

Implements the JSON-RPC 2.0 protocol over byte streams. The `@KsMethod` name maps to the JSON-RPC `method` field. Returns a `SingleChannelConnection` (no sub-services).

### Stdin/out (LSP-compatible)

```kotlin
import com.monkopedia.ksrpc.jsonrpc.asJsonRpcConnection

val env = ksrpcEnvironment { }
val connection = (stdinChannel to stdoutChannel)
    .asJsonRpcConnection(env)
connection.registerDefault(MyServiceImpl())
```

### Options

- `includeContentHeaders` (default `true`) -- include `Content-Length` headers per the LSP base protocol. Set to `false` for newline-delimited JSON-RPC.
- `cancellationConvention` -- configure JSON-RPC cancellation semantics.

Methods annotated with `@KsNotification` are sent as JSON-RPC notifications (no `id`, no response).

## Service Workers (experimental)

For browser JS/WASM, you can host a service inside a service worker.

### Worker script

```kotlin
import com.monkopedia.ksrpc.ksrpcEnvironment
import com.monkopedia.ksrpc.channels.registerDefault
import com.monkopedia.ksrpc.webworker.onServiceWorkerConnection

fun main() {
    val env = ksrpcEnvironment { }
    onServiceWorkerConnection(env) { connection ->
        connection.registerDefault(MyServiceImpl())
    }
}
```

### Main thread

```kotlin
import com.monkopedia.ksrpc.webworker.createServiceWorkerWithConnection

val env = ksrpcEnvironment { }
val connection = createServiceWorkerWithConnection("/worker.js", env)
val service = connection.defaultChannel().toStub<MyService>()
```
