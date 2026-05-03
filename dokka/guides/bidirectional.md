# Module bidirectional

# Bidirectional Communication

## Connection vs SingleChannelConnection

ksrpc provides two levels of bidirectional channels:

- **[`SingleChannelConnection`](https://monkopedia.github.io/ksrpc/ksrpc-core/com.monkopedia.ksrpc.channels/-single-channel-connection/index.html)** -- supports one hosted service and one client service (no sub-services). Used by JSON-RPC transport.
- **[`Connection`](https://monkopedia.github.io/ksrpc/ksrpc-core/com.monkopedia.ksrpc.channels/-connection/index.html)** -- extends `SingleChannelConnection` with full sub-service support in both directions. Used by WebSocket and socket transports.

Both provide `registerDefault` (host a service for incoming calls) and `defaultChannel` (get a channel for outgoing calls), and both can use them simultaneously.

## Basic pattern

```kotlin
val env = ksrpcEnvironment { }
withStdInOut(env) { connection ->
    // Host a service for the remote side to call
    connection.registerDefault(MyHostServiceImpl())

    // Get a stub for calling the remote side's service
    val remoteService = connection.defaultChannel().toStub<RemoteService>()
    val result = remoteService.someMethod("hello")
}
```

## The connect helper

The `connect<Host, Client>` extension combines `registerDefault` and `defaultChannel` into a single call. The lambda receives the client stub and returns the host service implementation:

```kotlin
withStdInOut(env) { connection ->
    connection.connect<MyHostService, MyClientService> { client ->
        // client is a MyClientService stub for calling the remote side
        MyHostServiceImpl(client) // returned value is registered as the hosted service
    }
}
```

The first type parameter is the service you host (returned by the lambda); the second is the remote service you call (passed into the lambda).

This is equivalent to:

```kotlin
withStdInOut(env) { connection ->
    val client = connection.defaultChannel().toStub<MyClientService>()
    connection.registerDefault(MyHostServiceImpl(client))
}
```

## Sub-service callbacks

On transports that support `Connection` (sockets, WebSockets, JNI), you can pass [`@KsService`](https://monkopedia.github.io/ksrpc/ksrpc-api/com.monkopedia.ksrpc.annotation/-ks-service/index.html) interfaces as method parameters and return values. This enables contextual callback patterns:

```kotlin
@KsService
interface ProgressCallback : RpcService {
    @KsMethod("/onProgress")
    suspend fun onProgress(percent: Int)

    @KsMethod("/onComplete")
    suspend fun onComplete()
}

@KsService
interface TaskRunner : RpcBidiService {
    @KsMethod("/run")
    suspend fun runTask(callback: ProgressCallback): String
}
```

The client passes a callback implementation. The server calls it during execution:

```kotlin
// Client side
val runner = connection.defaultChannel().toStub<TaskRunner>()
val result = runner.runTask(object : ProgressCallback {
    override suspend fun onProgress(percent: Int) {
        println("Progress: $percent%")
    }
    override suspend fun onComplete() {
        println("Done!")
    }
})
```

```kotlin
// Server side
class TaskRunnerImpl : TaskRunner {
    override suspend fun runTask(callback: ProgressCallback): String {
        callback.onProgress(0)
        doWork()
        callback.onProgress(50)
        doMoreWork()
        callback.onProgress(100)
        callback.onComplete()
        return "finished"
    }
}
```

Sub-service inputs require a [`ChannelClient`](https://monkopedia.github.io/ksrpc/ksrpc-core/com.monkopedia.ksrpc.channels/-channel-client/index.html) (i.e. a `Connection`). Sub-service outputs require a [`ChannelHost`](https://monkopedia.github.io/ksrpc/ksrpc-core/com.monkopedia.ksrpc.channels/-channel-host/index.html) (HTTP server or `Connection`). HTTP supports sub-service outputs but not inputs because it is not bidirectional.

## Nested sub-services

Sub-services can themselves return sub-services, enabling rich hierarchical APIs:

```kotlin
@KsService
interface TaskHandle : RpcService {
    @KsMethod("/cancel")
    suspend fun cancel()

    @KsMethod("/status")
    suspend fun status(): String
}

@KsService
interface Scheduler : RpcBidiService {
    @KsMethod("/schedule")
    suspend fun schedule(callback: ProgressCallback): TaskHandle
}
```

## Transport capabilities

| Transport | Sub-service input | Sub-service output | `connect<H, C>` |
|-----------|-------------------|--------------------|------------------|
| HTTP | No | Yes | No |
| WebSocket | Yes | Yes | Yes |
| Sockets | Yes | Yes | Yes |
| JSON-RPC | No | No | Yes (single channel) |
| JNI | Yes | Yes | Yes |
| Service Worker | Yes | Yes | Yes |

For Flow-based streaming over bidirectional channels, see the [flow streaming guide](flow-streaming.md).
