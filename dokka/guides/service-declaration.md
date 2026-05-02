# Module service-declaration

# Service Declaration

## Basics

Every ksrpc service is a Kotlin interface that:

1. Extends [RpcService]
2. Is annotated with `@KsService`
3. Has methods annotated with `@KsMethod` with a unique name within the service

```kotlin
@KsService
interface MyService : RpcService {
    @KsMethod("/doWork")
    suspend fun doWork(input: String): Int
}
```

The compiler plugin generates a companion [RpcObject] that provides stub creation and serialization adapters. Any method on the interface that is not annotated with `@KsMethod` will produce a compiler warning.

The `@KsMethod` name is the wire-level identifier. It must be unique within a single service but does not need to be globally unique. Choose stable names -- renaming breaks wire compatibility.

## Primitive types

Any primitive type supported by `kotlinx.serialization` can be used directly as an input or output: `String`, `Int`, `Long`, `Double`, `Float`, `Boolean`, `Byte`, `Short`, `Char`.

```kotlin
@KsService
interface MathService : RpcService {
    @KsMethod("/add")
    suspend fun add(a: Int): Int

    @KsMethod("/name")
    suspend fun name(): String
}
```

## Serializable types

Any `@Serializable` class can be used as input or output:

```kotlin
@Serializable
data class UserRequest(val name: String, val age: Int)

@Serializable
data class UserResponse(val id: String, val displayName: String)

@KsService
interface UserService : RpcService {
    @KsMethod("/createUser")
    suspend fun createUser(request: UserRequest): UserResponse
}
```

## Unit input and output

Methods with no parameters are supported directly -- you do not need a placeholder parameter:

```kotlin
@KsService
interface LifecycleService : RpcService {
    @KsMethod("/status")
    suspend fun getStatus(): StatusInfo

    @KsMethod("/ping")
    suspend fun ping(): String

    @KsMethod("/shutdown")
    suspend fun shutdown(reason: String)
}
```

The compiler plugin synthesizes the `Unit` handling internally for zero-argument methods.

> **Backward compatibility**: the older `u: Unit` parameter style still works and is equivalent to a zero-argument method. You may see it in older code:
>
> ```kotlin
> // Legacy style -- still supported but no longer required
> suspend fun getStatus(u: Unit): StatusInfo
> ```

## Binary data

Use [RpcBinaryData] for binary payloads. Binary transfer is streaming on HTTP and WebSocket transports. On socket transports the data is buffered in memory, and binary is not supported on JSON-RPC.

```kotlin
@KsService
interface FileService : RpcService {
    @KsMethod("/upload")
    suspend fun upload(data: RpcBinaryData): String

    @KsMethod("/download")
    suspend fun download(key: String): RpcBinaryData
}
```

### Binary data adapters

In addition to the core `RpcBinaryData` type, ksrpc provides adapter modules that let you use platform-specific binary stream types directly in your service signatures:

| Type | Module | Dependency |
|------|--------|------------|
| `ByteReadChannel` (ktor) | `ksrpc-binary-ktor` | `implementation("com.monkopedia.ksrpc:ksrpc-binary-ktor:$KSRPC_VERSION")` |
| `Source` (kotlinx-io) | `ksrpc-binary-kotlinx-io` | `implementation("com.monkopedia.ksrpc:ksrpc-binary-kotlinx-io:$KSRPC_VERSION")` |
| `BufferedSource` (okio) | `ksrpc-binary-okio` | `implementation("com.monkopedia.ksrpc:ksrpc-binary-okio:$KSRPC_VERSION")` |

Each adapter registers a transformer that converts between the platform type and `RpcBinaryData` on the wire. Usage is the same as `RpcBinaryData` -- just declare the adapter type in your method signature:

```kotlin
// Using ktor's ByteReadChannel (requires ksrpc-binary-ktor)
@KsService
interface StreamService : RpcService {
    @KsMethod("/stream")
    suspend fun stream(key: String): ByteReadChannel
}

// Using kotlinx-io Source (requires ksrpc-binary-kotlinx-io)
@KsService
interface IoService : RpcService {
    @KsMethod("/read")
    suspend fun read(key: String): Source
}

// Using okio BufferedSource (requires ksrpc-binary-okio)
@KsService
interface OkioService : RpcService {
    @KsMethod("/read")
    suspend fun read(key: String): BufferedSource
}
```

## Sub-services

A `@KsMethod` can accept or return another `@KsService` interface. This enables contextual callback patterns and service hierarchies:

```kotlin
@KsService
interface EntityService : RpcService {
    @KsMethod("/name")
    suspend fun getName(): String

    @KsMethod("/content")
    suspend fun getContent(): RpcBinaryData
}

@KsService
interface CatalogService : RpcService {
    @KsMethod("/get")
    suspend fun getEntity(id: Int): EntityService

    @KsMethod("/register")
    suspend fun registerEntity(entity: EntityService): Int
}
```

Sub-services as outputs require a transport that supports `ChannelHost` (HTTP server, `Connection`). Sub-services as inputs require `ChannelClient` (a `Connection`). See the [bidirectional guide](bidirectional.md) for details on callback patterns.

## Notifications

Use `@KsNotification` to mark a method as fire-and-forget on transports that support notification semantics (JSON-RPC):

```kotlin
@KsService
interface LogService : RpcService {
    @KsMethod("/log")
    @KsNotification
    suspend fun log(message: String)
}
```

The method must return `Unit`. On transports without notification support (HTTP, sockets), the call behaves as a normal request.

## Timeouts

Use `@KsTimeout` to specify a client-side timeout for a method call:

```kotlin
@KsService
interface SlowService : RpcService {
    @KsMethod("/compute")
    @KsTimeout(seconds = 30)
    suspend fun compute(input: String): String
}
```

The generated stub wraps the call in `withTimeout`. On transports with cancellation support, the cancellation propagates to the server.

## Implementing services

Implement a service by extending the interface:

```kotlin
class CatalogServiceImpl : CatalogService {
    override suspend fun getEntity(id: Int): EntityService = EntityImpl(id)
    override suspend fun registerEntity(entity: EntityService): Int {
        val name = entity.getName()
        return store.register(name)
    }
}
```

Convert between implementations and stubs using the generated companion:

```kotlin
// Implementation -> serialized service for hosting
val serialized = myImpl.serialized(env)

// Serialized channel -> typed stub for calling
val stub = channel.toStub<MyService>()
```

See [RpcObject] in the API docs for the full generated companion API.

## Related guides

- [Error Handling](error-handling.md) -- typed errors with `@KsError` on `@KsMethod` functions
- [Context Propagation](context-propagation.md) -- propagating request-scoped metadata with `@KsContext`
- [Bidirectional Communication](bidirectional.md) -- sub-service parameters, callbacks, and `connect<H, C>`
- [Flow Streaming](flow-streaming.md) -- using `Flow<T>` as a return type for streaming
- [Introspection](introspection.md) -- runtime discovery of service metadata
- [Transports](transports.md) -- which transports support binary data, sub-services, and notifications
