# Module transport-sockets

# Socket and Stdin/Stdout Transport

The socket transport uses a content-length-prefixed packet protocol over raw byte streams. It supports bidirectional communication with full sub-service support, and is well suited for direct TCP connections and subprocess stdin/stdout communication.

## Module and dependencies

```kotlin
implementation("com.monkopedia.ksrpc:ksrpc-sockets:$KSRPC_VERSION")
```

## Platform availability

| Platform | Supported | Notes |
|----------|-----------|-------|
| JVM | Yes | Uses `InputStream`/`OutputStream` or ktor byte channels |
| POSIX Native | Yes | Uses ktor `ByteReadChannel`/`ByteWriteChannel` |
| Windows Native | No | The implementation uses termios-based I/O |
| JS/WASM | No | |

## Socket server

```kotlin
import com.monkopedia.ksrpc.ksrpcEnvironment
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

## Socket client

```kotlin
import com.monkopedia.ksrpc.sockets.asConnection
import com.monkopedia.ksrpc.toStub

val env = ksrpcEnvironment { }
val socket = Socket("localhost", 1234)
val connection = (socket.getInputStream() to socket.getOutputStream())
    .asConnection(env)
val service = connection.defaultChannel().toStub<MyService>()
```

On Kotlin/Native, use `ByteReadChannel` / `ByteWriteChannel` pairs instead of JVM streams.

## Stdin/stdout

A convenience for the socket protocol over standard I/O streams. Useful for subprocess stdin/stdout communication where the parent process launches a child and communicates via its stdin/stdout.

```kotlin
import com.monkopedia.ksrpc.sockets.withStdInOut

val env = ksrpcEnvironment { }
withStdInOut(env) { connection ->
    connection.registerDefault(MyServiceImpl())
}
```

## Launching a subprocess

On JVM, `ProcessBuilder.asConnection` starts a subprocess and connects to it via its stdin/stdout:

```kotlin
import com.monkopedia.ksrpc.sockets.asConnection

val env = ksrpcEnvironment { }
val connection = ProcessBuilder("my-service-binary")
    .asConnection(env)
val service = connection.defaultChannel().toStub<MyService>()
```

The connection's `onClose` handler automatically destroys the subprocess.

## Transport semantics

- Uses Content-Length framing: each packet is prefixed with `Content-Length: N` headers followed by the JSON payload
- Returns a full `Connection<String>` supporting bidirectional communication
- Supports sub-services in both directions
- Binary data is buffered (not streamed like HTTP/WebSocket)
- Content-Length header framing compatible with LSP and similar protocols

## See also

- [Bidirectional communication](bidirectional.md) -- two-way RPC patterns
- [JSON-RPC transport](transport-jsonrpc.md) -- if you need LSP-compatible JSON-RPC semantics on top
- [Transports overview](transports.md) -- comparison of all transports
