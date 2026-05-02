# Module transport-websocket

# WebSocket Transport

The WebSocket transport provides bidirectional communication over ktor WebSocket sessions. Both sides can host services and invoke each other, making it suitable for interactive and event-driven APIs.

## Modules and dependencies

```kotlin
// Client
implementation("com.monkopedia.ksrpc:ksrpc-ktor-websocket-client:$KSRPC_VERSION")

// Server
implementation("com.monkopedia.ksrpc:ksrpc-ktor-websocket-server:$KSRPC_VERSION")

// Shared protocol (transitive dependency, usually not needed directly)
implementation("com.monkopedia.ksrpc:ksrpc-ktor-websocket-shared:$KSRPC_VERSION")
```

## Platform availability

| Component | JVM | Native | JS/WASM |
|-----------|-----|--------|---------|
| Client | Yes | Yes | Yes |
| Server | Yes | Yes | No |

## Server setup

```kotlin
import com.monkopedia.ksrpc.ksrpcEnvironment
import com.monkopedia.ksrpc.ktor.websocket.serveWebsocket
import io.ktor.server.websocket.WebSockets

val env = ksrpcEnvironment { }
embeddedServer(Netty, port = 8080) {
    install(WebSockets)
    routing {
        serveWebsocket("/ws/myservice", MyServiceImpl(), env)
    }
}.start(wait = true)
```

`serveWebsocket` also accepts a pre-serialized `SerializedService<String>`.

## Client setup

```kotlin
import com.monkopedia.ksrpc.ktor.websocket.asWebsocketConnection
import com.monkopedia.ksrpc.toStub
import io.ktor.client.plugins.websocket.WebSockets

val env = ksrpcEnvironment { }
val client = HttpClient { install(WebSockets) }
val connection = client.asWebsocketConnection("ws://localhost:8080/ws/myservice", env)
val service = connection.defaultChannel().toStub<MyService>()
```

## Transport semantics

- Uses a packet-based protocol over WebSocket frames
- Returns a full `Connection<String>` supporting bidirectional communication
- Supports sub-services in both directions (input and output `@KsService` parameters)
- Binary data is streamed via WebSocket binary frames
- The connection stays open until explicitly closed

## Bidirectional usage

The returned `Connection<String>` supports `registerDefault` for hosting a service back to the server:

```kotlin
val connection = client.asWebsocketConnection(url, env)

// Host a service for the server to call back
connection.registerDefault(MyCallbackImpl())

// Call the server's service
val service = connection.defaultChannel().toStub<MyService>()
```

You can also use the `connect` helper to set up both directions in one call:

```kotlin
connection.connect<MyCallback, MyService> { remoteService ->
    // remoteService is a stub for calling the server
    MyCallbackImpl(remoteService) // returned value is hosted for server callbacks
}
```

See the [bidirectional guide](bidirectional.md) for more patterns.

## See also

- [Bidirectional communication](bidirectional.md) -- two-way RPC patterns
- [Flow streaming](flow-streaming.md) -- `Flow`-based streaming over WebSocket
- [Error handling](error-handling.md) -- `@KsError` annotation and error routing
- [Transports overview](transports.md) -- comparison of all transports
