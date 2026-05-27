# Module service-declaration

# Service Declaration

## Basics

Every ksrpc service is a Kotlin interface that:

1. Extends [`RpcService`](https://monkopedia.github.io/ksrpc/ksrpc-api/com.monkopedia.ksrpc/-rpc-service/index.html)
2. Is annotated with [`@KsService`](https://monkopedia.github.io/ksrpc/ksrpc-api/com.monkopedia.ksrpc.annotation/-ks-service/index.html)
3. Has methods annotated with [`@KsMethod`](https://monkopedia.github.io/ksrpc/ksrpc-api/com.monkopedia.ksrpc.annotation/-ks-method/index.html) with a unique name within the service

```kotlin
@KsService
interface MyService : RpcService {
    @KsMethod("/doWork")
    suspend fun doWork(input: String): Int
}
```

The compiler plugin generates a companion [`RpcObject`](https://monkopedia.github.io/ksrpc/ksrpc-core/com.monkopedia.ksrpc/-rpc-object/index.html) that provides stub creation and serialization adapters. Any method on the interface that is not annotated with `@KsMethod` will produce a compiler warning.

> **One-parameter limit**: `@KsMethod` functions may accept at most one parameter (plus the implicit receiver). To pass multiple values, wrap them in a `@Serializable` data class.

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

Binary payloads are supported through platform-specific stream types. Add the adapter module for the I/O library you use, then declare the type directly in your method signatures. Binary transfer is streaming on HTTP and WebSocket transports; on socket transports the data is buffered in memory. Binary is not supported on JSON-RPC.

| Type | Module | Dependency |
|------|--------|------------|
| `ByteReadChannel` (ktor) | `ksrpc-binary-ktor` | `implementation("com.monkopedia.ksrpc:ksrpc-binary-ktor:$KSRPC_VERSION")` |
| `Source` (kotlinx-io) | `ksrpc-binary-kotlinx-io` | `implementation("com.monkopedia.ksrpc:ksrpc-binary-kotlinx-io:$KSRPC_VERSION")` |
| `BufferedSource` (okio) | `ksrpc-binary-okio` | `implementation("com.monkopedia.ksrpc:ksrpc-binary-okio:$KSRPC_VERSION")` |

```kotlin
// Using ktor's ByteReadChannel (requires ksrpc-binary-ktor)
@KsService
interface FileService : RpcService {
    @KsMethod("/upload")
    suspend fun upload(data: ByteReadChannel): String

    @KsMethod("/download")
    suspend fun download(key: String): ByteReadChannel
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

Each adapter module registers a transformer that the compiler plugin uses to convert between the platform type and the internal wire format. You only need the adapter module on your classpath -- no extra configuration required.

## Sub-services

A `@KsMethod` can accept or return another `@KsService` interface. This enables contextual callback patterns and service hierarchies:

```kotlin
@KsService
interface EntityService : RpcService {
    @KsMethod("/name")
    suspend fun getName(): String

    @KsMethod("/content")
    suspend fun getContent(): ByteReadChannel
}

@KsService
interface CatalogService : RpcBidiService {
    @KsMethod("/get")
    suspend fun getEntity(id: Int): EntityService

    @KsMethod("/register")
    suspend fun registerEntity(entity: EntityService): Int
}
```

Sub-services as outputs require a transport that supports [`ChannelHost`](https://monkopedia.github.io/ksrpc/ksrpc-core/com.monkopedia.ksrpc.channels/-channel-host/index.html) (HTTP server, [`Connection`](https://monkopedia.github.io/ksrpc/ksrpc-core/com.monkopedia.ksrpc.channels/-connection/index.html)). Sub-services as inputs require [`ChannelClient`](https://monkopedia.github.io/ksrpc/ksrpc-core/com.monkopedia.ksrpc.channels/-channel-client/index.html) (a `Connection`). See the [bidirectional guide](bidirectional.md) for details on callback patterns.

## Notifications

Use [`@KsNotification`](https://monkopedia.github.io/ksrpc/ksrpc-api/com.monkopedia.ksrpc.annotation/-ks-notification/index.html) to mark a method as fire-and-forget on transports that support notification semantics (JSON-RPC):

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

Use [`@KsTimeout`](https://monkopedia.github.io/ksrpc/ksrpc-api/com.monkopedia.ksrpc.annotation/-ks-timeout/index.html) to specify a client-side timeout for a method call:

```kotlin
@KsService
interface SlowService : RpcService {
    @KsMethod("/compute")
    @KsTimeout(seconds = 30)
    suspend fun compute(input: String): String
}
```

The generated stub wraps the call in `withTimeout`. On transports with cancellation support, the cancellation propagates to the server.

## Result return types

A `@KsMethod` may return `Result<O>`. Such a method is equivalent to a plain `O`-returning method wrapped in `runCatching`-except-cancellation:

```kotlin
@KsService
interface ParseService : RpcService {
    @KsMethod("/parse")
    @KsError(code = 200, type = ParseError::class)
    suspend fun parse(input: String): Result<Int>
}
```

The server handler returns a `Result`:

```kotlin
class ParseServiceImpl : ParseService {
    override suspend fun parse(input: String): Result<Int> =
        input.toIntOrNull()?.let { Result.success(it) }
            ?: Result.failure(ParseError(input))
}
```

The client stub returns a `Result` and does **not** throw on failure -- handle the outcome with `onSuccess` / `onFailure`, `getOrNull`, `fold`, or any other `Result` API:

```kotlin
stub.parse("42")
    .onSuccess { value -> println("Parsed $value") }
    .onFailure { error -> println("Failed: ${error.message}") }
```

The semantics are:

- **No wire change.** A `Result<O>` method is byte-for-byte identical to a plain `O` method on the wire. Success serializes the inner `O` exactly as a plain `O` method would; failure uses the existing [`@KsError`](https://monkopedia.github.io/ksrpc/ksrpc-api/com.monkopedia.ksrpc.annotation/-ks-error/index.html) / error envelope. `kotlin.Result` itself is never serialized. A peer written against `Result<O>` and one written against `O` interoperate.
- **Server.** A returned `Result.success(o)` sends `o`. A returned `Result.failure(e)` is encoded through the same path as a thrown `e`, so it is indistinguishable on the wire from `throw e`. Throwing from the handler also surfaces as `Result.failure` on the client.
- **Client.** An error response becomes `Result.failure(decoded)`; a success response becomes `Result.success(o)`. The stub never throws except for cancellation.
- **`@KsError` participates unchanged.** A bound typed error round-trips into `Result.failure(typedError)`; an unmapped failure becomes a generic `KsrpcException` inside `Result.failure`. See [Error Handling](error-handling.md).
- **Cancellation propagates.** `CancellationException` and `TimeoutCancellationException` are **not** folded into the `Result` -- they propagate on both sides to preserve structured concurrency (this is the "except cancellation" part of `runCatching`-except-cancellation). A `@KsTimeout` firing on a `Result<O>` method throws, it does not return `Result.failure`.

### Unsupported nested shapes

`Result` is supported only as the top-level type wrapping a serializable or binary value. The following shapes are rejected by a compiler (FIR) diagnostic, on both the return type and the parameter type:

- `Result<Flow<…>>`
- `Flow<Result<…>>` (`Result` cannot be nested inside `Flow`)
- `Result<SubService>` (a `Result` wrapping a `@KsService` sub-service)
- `Result<Result<…>>` (nested `Result`)

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

See [`RpcObject`](https://monkopedia.github.io/ksrpc/ksrpc-core/com.monkopedia.ksrpc/-rpc-object/index.html) in the API docs for the full generated companion API.

## Related guides

- [Error Handling](error-handling.md) -- typed errors with [`@KsError`](https://monkopedia.github.io/ksrpc/ksrpc-api/com.monkopedia.ksrpc.annotation/-ks-error/index.html) on `@KsMethod` functions
- [Context Propagation](context-propagation.md) -- propagating request-scoped metadata with [`@KsContext`](https://monkopedia.github.io/ksrpc/ksrpc-api/com.monkopedia.ksrpc.annotation/-ks-context/index.html)
- [Bidirectional Communication](bidirectional.md) -- sub-service parameters, callbacks, and `connect<H, C>`
- [Flow Streaming](flow-streaming.md) -- using `Flow<T>` as a return type for streaming
- [Introspection](introspection.md) -- runtime discovery of service metadata
- [Transports](transports.md) -- which transports support binary data, sub-services, and notifications
