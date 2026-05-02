# Module transport-http

# HTTP Transport

The HTTP transport maps each [`@KsMethod`](https://monkopedia.github.io/ksrpc/ksrpc-api/com.monkopedia.ksrpc.annotation/-ks-method/index.html) call to an HTTP POST request. It integrates with ktor on both client and server, providing the simplest path to exposing ksrpc services over the network.

HTTP is request/response only -- it returns a [`ChannelClient`](https://monkopedia.github.io/ksrpc/ksrpc-core/com.monkopedia.ksrpc.channels/-channel-client/index.html), not a bidirectional [`Connection`](https://monkopedia.github.io/ksrpc/ksrpc-core/com.monkopedia.ksrpc.channels/-connection/index.html). For bidirectional communication, use the [WebSocket](transport-websocket.md) or [socket](transport-sockets.md) transports instead.

## Modules and dependencies

```kotlin
// Client
implementation("com.monkopedia.ksrpc:ksrpc-ktor-client:$KSRPC_VERSION")

// Server
implementation("com.monkopedia.ksrpc:ksrpc-ktor-server:$KSRPC_VERSION")
```

## Platform availability

| Component | JVM | Native | JS/WASM |
|-----------|-----|--------|---------|
| Client | Yes | Yes | Yes |
| Server | Yes | Yes | No |

## Server setup (ktor)

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

`serveHttp` also accepts a pre-serialized [`SerializedService`](https://monkopedia.github.io/ksrpc/ksrpc-core/com.monkopedia.ksrpc.channels/-serialized-service/index.html)`<String>` or a `SerializedChannel<String>`, giving you control over when serialization setup happens.

## Client setup

```kotlin
import com.monkopedia.ksrpc.ktor.asHttpChannelClient
import com.monkopedia.ksrpc.toStub

val env = ksrpcEnvironment { }
val channelClient = HttpClient { }
    .asHttpChannelClient("http://localhost:8080/api/myservice", env)
val service = channelClient.defaultChannel().toStub<MyService>()
```

## Transport semantics

- Each `@KsMethod` call is a POST to `{basePath}/call/{methodName}`
- Request and response bodies are JSON-encoded via `kotlinx.serialization`
- Binary data ([`RpcBinaryData`](https://monkopedia.github.io/ksrpc/ksrpc-core/com.monkopedia.ksrpc.channels/-rpc-binary-data/index.html) parameters/returns) is streamed via ktor's `ByteReadChannel`/`ByteWriteChannel`, flagged with a `binary: true` header
- Sub-service outputs are supported (the server can return `@KsService` interfaces), but sub-service inputs are not (the client cannot pass callback services to the server)

## Error mapping

HTTP maps ksrpc error codes to HTTP status codes. The default mapping:

| ksrpc code | HTTP status |
|------------|-------------|
| `ENDPOINT_NOT_FOUND_CODE` (-32601) | 404 |
| `INTERNAL_ERROR_CODE` (-32603) | 500 |

Custom [`@KsError`](https://monkopedia.github.io/ksrpc/ksrpc-api/com.monkopedia.ksrpc.annotation/-ks-error/index.html) codes default to HTTP 500, with the original code carried in the `X-Ksrpc-Error-Code` header and the message in `X-Ksrpc-Error-Message`. You can provide a custom mapping on both ends:

```kotlin
val errorMap = mapOf(
    100 to 422, // map your custom error code to an HTTP status
    KsrpcException.ENDPOINT_NOT_FOUND_CODE to 404,
    KsrpcException.INTERNAL_ERROR_CODE to 500
)

// Server
serveHttp("/api/myservice", service, env, errorCodeToHttpStatus = errorMap)

// Client
httpClient.asHttpChannelClient(url, env, errorCodeToHttpStatus = errorMap)
```

Pass the same map on both ends so the round-trip preserves user-defined error codes.

## Context propagation

[`@KsContext`](https://monkopedia.github.io/ksrpc/ksrpc-api/com.monkopedia.ksrpc.annotation/-ks-context/index.html) bindings are propagated via HTTP headers. See the [context propagation guide](context-propagation.md) for details on defining and registering context bindings.

## See also

- [Error handling](error-handling.md) -- `@KsError` annotation and error routing
- [Context propagation](context-propagation.md) -- `@KsContext` across transports
- [Transports overview](transports.md) -- comparison of all transports
