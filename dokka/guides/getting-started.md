# Module getting-started

# Getting Started

## Gradle setup

Add the ksrpc compiler plugin and runtime dependency to your `build.gradle.kts`:

```kotlin
plugins {
    kotlin("multiplatform") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
    id("com.monkopedia.ksrpc.plugin") version "$KSRPC_VERSION"
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation("com.monkopedia.ksrpc:ksrpc-core:$KSRPC_VERSION")
            }
        }
    }
}
```

For transport-specific dependencies, add the appropriate modules (see the [transports guide](transports.md)):

```kotlin
// HTTP client
implementation("com.monkopedia.ksrpc:ksrpc-ktor-client:$KSRPC_VERSION")
// HTTP server
implementation("com.monkopedia.ksrpc:ksrpc-ktor-server:$KSRPC_VERSION")
// WebSocket client
implementation("com.monkopedia.ksrpc:ksrpc-ktor-websocket-client:$KSRPC_VERSION")
// WebSocket server
implementation("com.monkopedia.ksrpc:ksrpc-ktor-websocket-server:$KSRPC_VERSION")
// Sockets / stdin-out
implementation("com.monkopedia.ksrpc:ksrpc-sockets:$KSRPC_VERSION")
// JSON-RPC 2.0
implementation("com.monkopedia.ksrpc:ksrpc-jsonrpc:$KSRPC_VERSION")
// Flow streaming
implementation("com.monkopedia.ksrpc:ksrpc-flow:$KSRPC_VERSION")
// Introspection
implementation("com.monkopedia.ksrpc:ksrpc-introspection:$KSRPC_VERSION")
```

## Define a service

Declare your service as an interface extending [`RpcService`](https://monkopedia.github.io/ksrpc/ksrpc-api/com.monkopedia.ksrpc/-rpc-service/index.html), annotated with [`@KsService`](https://monkopedia.github.io/ksrpc/ksrpc-api/com.monkopedia.ksrpc.annotation/-ks-service/index.html). Tag each method with [`@KsMethod`](https://monkopedia.github.io/ksrpc/ksrpc-api/com.monkopedia.ksrpc.annotation/-ks-method/index.html) and a unique name:

```kotlin
import com.monkopedia.ksrpc.RpcService
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService

@KsService
interface GreetingService : RpcService {
    @KsMethod("/greet")
    suspend fun greet(name: String): String
}
```

The compiler plugin generates a companion [`RpcObject`](https://monkopedia.github.io/ksrpc/ksrpc-core/com.monkopedia.ksrpc/-rpc-object/index.html) and stub implementations automatically.

## Implement the service

```kotlin
class GreetingServiceImpl : GreetingService {
    override suspend fun greet(name: String): String = "Hello, $name!"
}
```

## Host over HTTP

```kotlin
import com.monkopedia.ksrpc.ksrpcEnvironment
import com.monkopedia.ksrpc.ktor.serveHttp
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing

fun main() {
    val env = ksrpcEnvironment { }
    embeddedServer(Netty, port = 8080) {
        routing {
            serveHttp("/api/greeting", GreetingServiceImpl(), env)
        }
    }.start(wait = true)
}
```

## Call from a client

```kotlin
import com.monkopedia.ksrpc.ksrpcEnvironment
import com.monkopedia.ksrpc.ktor.asHttpChannelClient
import com.monkopedia.ksrpc.toStub
import io.ktor.client.HttpClient

suspend fun main() {
    val env = ksrpcEnvironment { }
    val client = HttpClient { }
        .asHttpChannelClient("http://localhost:8080/api/greeting", env)
    val service = client.defaultChannel().toStub<GreetingService>()

    println(service.greet("world")) // prints "Hello, world!"
}
```

## Environment configuration

All channels share a [`KsrpcEnvironment`](https://monkopedia.github.io/ksrpc/ksrpc-core/com.monkopedia.ksrpc/-ksrpc-environment/index.html) that holds the serialization format, default coroutine scope, and error handling:

```kotlin
val env = ksrpcEnvironment {
    serialization = Json {
        encodeDefaults = true
    }
    errorListener = ErrorListener { t ->
        t.printStackTrace()
    }
}
```

See [`KsrpcEnvironment`](https://monkopedia.github.io/ksrpc/ksrpc-core/com.monkopedia.ksrpc/-ksrpc-environment/index.html) in the API docs for the full set of configurable fields.

## Next steps

- [Service Declaration](service-declaration.md) -- full reference on types, annotations, and patterns
- [Transports](transports.md) -- all supported transports with setup code
- [Bidirectional Communication](bidirectional.md) -- two-way connections and callbacks
- [Error Handling](error-handling.md) -- typed errors with [`@KsError`](https://monkopedia.github.io/ksrpc/ksrpc-api/com.monkopedia.ksrpc.annotation/-ks-error/index.html)
- [Context Propagation](context-propagation.md) -- propagating auth tokens, trace IDs, and other metadata with [`@KsContext`](https://monkopedia.github.io/ksrpc/ksrpc-api/com.monkopedia.ksrpc.annotation/-ks-context/index.html)
- [Flow Streaming](flow-streaming.md) -- streaming data with `Flow<T>`
- [Introspection](introspection.md) -- runtime service metadata discovery
