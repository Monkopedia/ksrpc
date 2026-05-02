# Module transport-service-worker

# Service Worker Transport (Experimental)

The service worker transport hosts a ksrpc service inside a browser service worker, allowing web applications to offload RPC processing to a background thread. Communication uses the `MessagePort` API.

This transport is experimental and requires explicit opt-in:

```kotlin
@OptIn(ExperimentalKsrpc::class)
```

## Module and dependencies

```kotlin
implementation("com.monkopedia.ksrpc:ksrpc-service-worker:$KSRPC_VERSION")
```

## Platform availability

| Platform | Supported |
|----------|-----------|
| JS | Yes |
| WASM | Yes |
| JVM | No |
| Native | No |

## Worker entry point

In the service worker script, use `onServiceWorkerConnection` to listen for incoming connections:

```kotlin
import com.monkopedia.ksrpc.annotation.ExperimentalKsrpc
import com.monkopedia.ksrpc.ksrpcEnvironment
import com.monkopedia.ksrpc.channels.registerDefault
import com.monkopedia.ksrpc.webworker.onServiceWorkerConnection

@OptIn(ExperimentalKsrpc::class)
fun main() {
    val env = ksrpcEnvironment { }
    onServiceWorkerConnection(env) { connection ->
        connection.registerDefault(MyServiceImpl())
    }
}
```

The worker listens for `"ksrpc-connect"` messages and establishes a `Connection` for each incoming client via the message's `MessagePort`.

## Client side

From the main thread, use `createServiceWorkerWithConnection` to register the worker and obtain a connection:

```kotlin
import com.monkopedia.ksrpc.annotation.ExperimentalKsrpc
import com.monkopedia.ksrpc.ksrpcEnvironment
import com.monkopedia.ksrpc.toStub
import com.monkopedia.ksrpc.webworker.createServiceWorkerWithConnection

@OptIn(ExperimentalKsrpc::class)
fun main() {
    val env = ksrpcEnvironment { }
    val connection = createServiceWorkerWithConnection("/worker.js", env)
    val service = connection.defaultChannel().toStub<MyService>()
}
```

## Transport semantics

- Returns a full `Connection<String>` supporting bidirectional communication
- Supports sub-services in both directions
- Binary data is supported
- Communication uses the browser `MessagePort` / `MessageChannel` API
- The worker script path is relative to the application's origin

## See also

- [Bidirectional communication](bidirectional.md) -- two-way RPC patterns
- [Transports overview](transports.md) -- comparison of all transports
