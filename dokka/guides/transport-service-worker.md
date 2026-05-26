# Module transport-service-worker

# Service Worker Transport (Experimental)

The service worker transport hosts a ksrpc service inside a browser service worker, allowing web applications to offload RPC processing to a background thread. Communication uses the `MessagePort` API.

This transport is experimental and requires explicit opt-in with [`@ExperimentalKsrpc`](https://monkopedia.github.io/ksrpc/ksrpc-api/com.monkopedia.ksrpc.annotation/-experimental-ksrpc/index.html):

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

## Build setup

The worker is a separate browser script that must be built and served on its own, so the code above is only half of what a working setup needs. The worker entry point compiles to a standalone JavaScript bundle, which is served at the path the client passes to `createServiceWorkerWithConnection`.

Build the worker as its own JS executable that depends on `ksrpc-service-worker`, using webpack to produce the bundle:

```kotlin
kotlin {
    js {
        binaries.executable()
    }
    sourceSets["jsMain"].dependencies {
        implementation("com.monkopedia.ksrpc:ksrpc-service-worker:$KSRPC_VERSION")
        implementation(npm("webpack", "5.101.3"))
        implementation(npm("webpack-cli", "6.0.1"))
        implementation(npm("source-map-loader", "5.0.0"))
    }
}
```

`jsBrowserProductionWebpack` / `jsBrowserDistribution` produce the worker bundle under `build/dist/js/productionExecutable`. Serve that `.js` at the origin-relative path the client registers (e.g. `/worker.js`).

The repository's own modules are the working end-to-end reference for this wiring:

- [`ksrpc-service-worker-test`](https://github.com/Monkopedia/ksrpc/blob/main/ksrpc-service-worker-test/build.gradle.kts) -- the worker built as a standalone JS executable.
- [`ksrpc-test`](https://github.com/Monkopedia/ksrpc/blob/main/ksrpc-test/build.gradle.kts) -- the `copyWebWorker*` tasks that stage the built worker bundle into resources and serve it for the client to register.

## Transport semantics

- Returns a full [`Connection`](https://monkopedia.github.io/ksrpc/ksrpc-core/com.monkopedia.ksrpc.channels/-connection/index.html)`<String>` supporting bidirectional communication
- Supports sub-services in both directions
- Binary data is supported
- Communication uses the browser `MessagePort` / `MessageChannel` API
- The worker script path is relative to the application's origin

## See also

- [Bidirectional communication](bidirectional.md) -- two-way RPC patterns
- [Transports overview](transports.md) -- comparison of all transports
