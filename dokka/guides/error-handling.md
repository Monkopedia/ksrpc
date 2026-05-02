# Module error-handling

# Error Handling

## KsrpcException

All ksrpc runtime errors extend [KsrpcException], which carries:

- `code` -- an integer error code (used on the wire)
- `message` -- a human-readable description
- `data` -- an optional typed payload (populated when `@KsError` bindings are active)
- `cause` -- optional root cause (server-side only; not propagated across the wire)

Built-in subclasses:

- `RpcException` -- generic wire error, `code = -32603` (JSON-RPC "internal error"). HTTP maps to 500.
- `RpcEndpointException` -- method not found, `code = -32601` (JSON-RPC "method not found"). HTTP maps to 404.

## Untyped errors

By default, exceptions thrown in a service handler are caught by the runtime, reported to the configured `ErrorListener`, and sent to the client as a generic `KsrpcException` with the message string. The full stack trace stays server-side.

```kotlin
class MyServiceImpl : MyService {
    override suspend fun riskyCall(input: String): String {
        throw IllegalStateException("something went wrong")
    }
}

// Client side
try {
    service.riskyCall("test")
} catch (e: KsrpcException) {
    // e.code == -32603 (internal error)
    // e.message contains "something went wrong"
}
```

## @KsError: typed error bindings

Use `@KsError` to bind `@Serializable` exception types to integer error codes on specific methods. This gives clients typed exceptions they can catch directly.

### Step 1: Define the error type

The error class must be `@Serializable` and extend `Throwable` (typically `RuntimeException`). Only declare fields that are safe to serialize -- avoid `cause` and `stackTrace`:

```kotlin
@Serializable
class AuthError(val reason: String) : RuntimeException() {
    override val message: String get() = "Authentication failed: $reason"
}

@Serializable
class RateLimitError(val retryAfterMs: Long) : RuntimeException() {
    override val message: String get() = "Rate limited, retry after ${retryAfterMs}ms"
}
```

### Step 2: Bind errors to methods

Apply `@KsError` annotations to `@KsMethod` functions. Each binding maps a type to a unique integer code:

```kotlin
@KsService
interface AuthService : RpcService {
    @KsMethod("/login")
    @KsError(code = 100, type = AuthError::class)
    @KsError(code = 101, type = RateLimitError::class)
    suspend fun login(credentials: Credentials): AuthToken
}
```

`@KsError` is `@Repeatable` -- you can bind multiple error types to the same method. Each code must be unique within the method.

### Step 3: Throw from the handler

Throw the bound type directly from your service implementation. No wrapper is needed:

```kotlin
class AuthServiceImpl : AuthService {
    override suspend fun login(credentials: Credentials): AuthToken {
        if (!isValid(credentials)) {
            throw AuthError("invalid credentials")
        }
        if (isRateLimited(credentials.userId)) {
            throw RateLimitError(retryAfterMs = 5000)
        }
        return generateToken(credentials)
    }
}
```

### Step 4: Catch on the client

The client receives the original typed exception, deserialized from the wire:

```kotlin
try {
    service.login(credentials)
} catch (e: AuthError) {
    println("Auth failed: ${e.reason}")
} catch (e: RateLimitError) {
    delay(e.retryAfterMs)
    retry()
} catch (e: KsrpcException) {
    // Fallback for unbound errors
    println("RPC error ${e.code}: ${e.message}")
}
```

### Wire behavior

- The `code` in `@KsError` is the single source of truth for the wire code. There is no per-throw override.
- The server serializes the throwable using its `KSerializer` and sends the code + serialized payload.
- The client deserializes back into the bound type and re-throws it.
- Server stack traces are NOT propagated -- clients see a stack from local deserialization. Use `message` and serialized fields for diagnostics.

## ErrorListener

Configure a global error listener in [KsrpcEnvironment] to observe all errors:

```kotlin
val env = ksrpcEnvironment {
    errorListener = ErrorListener { t ->
        logger.error("RPC error", t)
    }
}
```

Create a local environment with a different error handler using `onError`:

```kotlin
val localEnv = env.onError { t ->
    metrics.recordError(t)
}
```

The `ErrorListener` is called for all errors -- both untyped and typed. It runs on the server side before the error is sent to the client.
