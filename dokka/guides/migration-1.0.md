# Module migration-1-0

# Migration Guide: 0.11.x to 1.0

## Service tier hierarchy (breaking)

Services must now declare their capability tier explicitly:

| Your service... | Must extend |
|----------------|-------------|
| Only has simple input/output methods | `RpcService` (unchanged) |
| Returns sub-services | `RpcHostService` |
| Accepts sub-service inputs or uses `Flow<T>` | `RpcBidiService` |

**Before (0.11.x):**
```kotlin
@KsService
interface MyService : RpcService {
    @KsMethod("/getData")
    suspend fun getData(id: String): Data

    @KsMethod("/getChild")
    suspend fun getChild(id: String): ChildService
}
```

**After (1.0):**
```kotlin
@KsService
interface MyService : RpcHostService {  // Changed: returns a sub-service
    @KsMethod("/getData")
    suspend fun getData(id: String): Data

    @KsMethod("/getChild")
    suspend fun getChild(id: String): ChildService
}
```

The compiler plugin will emit a clear error telling you exactly which tier is needed and why:

> "MyService extends RpcService but method 'getChild' returns ChildService (a sub-service). Change 'RpcService' to 'RpcHostService'."

### Quick reference

- `RpcService` -- simple methods only
- `RpcHostService` -- any method returning a `@KsService` type
- `RpcBidiService` -- any method accepting a `@KsService` input, or returning `Flow<T>`
- `IntrospectableRpcService` -- now extends `RpcHostService` (was `RpcService`)

## @KsService interface inheritance (new)

You can now have `@KsService` interfaces that extend other `@KsService` interfaces:

```kotlin
@KsService
interface CoreApi : RpcService {
    @KsMethod("/version")
    suspend fun version(): String
}

@KsService
interface ExtendedApi : CoreApi, RpcBidiService {
    @KsMethod("/stream")
    suspend fun stream(filter: String): Flow<Update>
}
```

`rpcObject<ExtendedApi>()` includes all methods from `CoreApi` plus its own.

## Runtime tier checks (new)

Registering a service on a transport that doesn't support its tier now throws immediately at registration time instead of failing later at call time:

```
IllegalArgumentException: MyService requires HOST transport capability,
but JSON-RPC (SingleChannelConnection) only supports up to SIMPLE.
```

## Binary data (new modules)

Binary payload support moved from `ByteReadChannel` hardcoded in core to adapter modules:

| Type | Module |
|------|--------|
| `ByteReadChannel` (ktor) | `ksrpc-binary-ktor` |
| `Source` (kotlinx-io) | `ksrpc-binary-kotlinx-io` |
| `BufferedSource` (okio) | `ksrpc-binary-okio` |

If you were using `ByteReadChannel` in method signatures, add `ksrpc-binary-ktor` to your dependencies.

## @KsContext (new)

Per-call context propagation via `@KsContext` annotations. See the [context propagation guide](context-propagation.md).

## @KsError (new)

Typed error mappings via `@KsError`. See the [error handling guide](error-handling.md).

## API cleanup

- Several internal types now properly gated behind `@KsrpcInternal` (removed from public API surface)
- `Packet` class is `@KsrpcInternal`
- `ServiceTier` enum added to public API
- `RpcObject.serviceTier` property added (compiler-generated)

## BCV consumers: re-dump after upgrade

If you use [binary-compatibility-validator](https://github.com/Kotlin/binary-compatibility-validator), `apiCheck` will fail after upgrading because generated `Stub$*` classes reference internal types that moved packages. Run:

```
./gradlew apiDump
```

after the upgrade to refresh your API dumps.

A future release will annotate generated synthetic classes with `@KsrpcInternal` so BCV's `nonPublicMarkers` filter excludes them automatically.
