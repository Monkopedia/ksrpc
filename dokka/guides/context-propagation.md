# Module context-propagation

# Context Propagation

ksrpc can propagate coroutine context elements across the wire on a per-call basis. This is useful for authentication tokens, trace IDs, and similar request-scoped metadata.

## Defining a context binding

A context binding is a `CoroutineContext.Element` paired with a [KsContextBinding] that describes how to encode/decode it for the wire. The recommended pattern uses a named companion object:

```kotlin
import com.monkopedia.ksrpc.KsContextBinding
import kotlin.coroutines.CoroutineContext

class AuthToken(val token: String) : CoroutineContext.Element {
    override val key get() = Key

    companion object Key : KsContextBinding<AuthToken> {
        override val wireKey = "x-auth-token"
        override fun toWire(value: AuthToken) = value.token
        override fun fromWire(encoded: String) = AuthToken(encoded)
    }
}
```

The binding must implement:

- `wireKey` -- a stable string identifier used by transports (e.g. as an HTTP header name, a JSON-RPC metadata key, or a packet protocol field). Must be unique across all bindings on a given method.
- `toWire(value)` -- encode the element to a string for transmission.
- `fromWire(encoded)` -- decode a string back into the element.

## Creating a context annotation

Wrap your binding in an annotation meta-annotated with `@KsContext`:

```kotlin
import com.monkopedia.ksrpc.annotation.KsContext

@KsContext(binding = AuthToken.Key::class)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class Auth
```

## Applying to services and methods

Apply the annotation at the service level (all methods) or on individual methods:

```kotlin
@Auth  // applies to all methods in this service
@KsService
interface SecureService : RpcService {
    @KsMethod("/data")
    suspend fun getData(key: String): String

    @KsMethod("/admin")
    @WithTrace  // additional context binding on this method only
    suspend fun adminAction(command: String): String
}
```

Multiple context annotations can be applied to the same method. The compiler rejects duplicate `wireKey` values at compile time.

## Setting context on the caller side

Use standard `withContext` to install the element before making a call:

```kotlin
import kotlinx.coroutines.withContext

withContext(AuthToken("my-secret-token")) {
    val result = service.getData("key")
}
```

The generated stub reads the context element from the coroutine context and encodes it onto the wire using the binding's `toWire`.

## Reading context on the handler side

The handler reads the propagated value from `coroutineContext` using the binding's key:

```kotlin
class SecureServiceImpl : SecureService {
    override suspend fun getData(key: String): String {
        val auth = coroutineContext[AuthToken.Key]
            ?: throw IllegalStateException("No auth token")
        validateToken(auth.token)
        return fetchData(key)
    }

    override suspend fun adminAction(command: String): String {
        val auth = coroutineContext[AuthToken.Key]
            ?: throw IllegalStateException("No auth token")
        val trace = coroutineContext[TraceId.Key]
        logger.info("Admin action by ${auth.token}, trace=${trace?.value}")
        return executeCommand(command)
    }
}
```

Note: with a named companion pattern, use `coroutineContext[AuthToken.Key]` (the companion object), not `coroutineContext[AuthToken]` (which resolves to the class, not the key).

## Absent context

If the caller does not install a context element, the handler sees `null` from the `coroutineContext[Key]` lookup. Your handler should handle this case -- either with a default value or by throwing an error.

## Compiler validation

The compiler plugin performs these checks:

- A `@KsContext`-annotated annotation must reference a `binding` class that implements [KsContextBinding].
- Two `@KsContext` annotations on the same method (or inherited from the service level) must not declare the same `wireKey`.

## Wire transport

Context values are carried differently depending on the transport:

- **Packet protocol** (sockets, WebSockets): encoded in an optional `cx` field on the packet.
- **HTTP**: could be carried as request headers keyed by `wireKey`.
- **JSON-RPC**: could be carried in a metadata object on the request.

The exact wire encoding for each transport is handled internally. From your perspective, you set context with `withContext` and read it with `coroutineContext[Key]`.
