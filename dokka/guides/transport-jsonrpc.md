# Module transport-jsonrpc

# JSON-RPC 2.0 Transport

The JSON-RPC transport implements the [JSON-RPC 2.0](https://www.jsonrpc.org/specification) protocol over byte streams. This makes ksrpc services interoperable with any JSON-RPC 2.0 client or server, including LSP implementations.

The JSON-RPC transport returns a `SingleChannelConnection` rather than a full `Connection` -- it does not support sub-services. Each `@KsMethod` name maps directly to the JSON-RPC `method` field.

## Module and dependencies

```kotlin
implementation("com.monkopedia.ksrpc:ksrpc-jsonrpc:$KSRPC_VERSION")
```

## Platform availability

| Platform | Supported |
|----------|-----------|
| JVM | Yes |
| POSIX Native | Yes |
| Windows Native | No |
| JS/WASM | No |

## Setup (stdin/stdout, LSP-compatible)

```kotlin
import com.monkopedia.ksrpc.ksrpcEnvironment
import com.monkopedia.ksrpc.jsonrpc.asJsonRpcConnection

val env = ksrpcEnvironment { }
val connection = (stdinChannel to stdoutChannel)
    .asJsonRpcConnection(env)
connection.registerDefault(MyServiceImpl())
```

The input/output pair is `Pair<ByteReadChannel, ByteWriteChannel>`, the same as the socket transport.

## Options

`asJsonRpcConnection` accepts the following parameters:

- **`includeContentHeaders`** (default `true`) -- when `true`, each message is prefixed with `Content-Length` headers per the LSP base protocol. Set to `false` for newline-delimited JSON-RPC (one JSON object per line).

- **`cancellationConvention`** (default `JsonRpcCancellationConvention.None`) -- configures how cancellation is communicated on the wire:
  - `None` -- cancellation is local only; no notification is sent to the remote side.
  - `Notification(method)` -- sends a JSON-RPC notification with the given method name and `{ "id": <original id> }` params. Incoming notifications with that method cancel the matching handler.
  - `JsonRpcCancellationConvention.Lsp` -- convenience for the LSP `$/cancelRequest` convention.

```kotlin
// LSP-compatible with cancellation support
val connection = (input to output).asJsonRpcConnection(
    env,
    includeContentHeaders = true,
    cancellationConvention = JsonRpcCancellationConvention.Lsp
)
```

## Notifications

Methods annotated with `@KsNotification` are sent as JSON-RPC notifications (no `id` field, no response expected). This is useful for fire-and-forget messages like LSP's `textDocument/didOpen`.

```kotlin
@KsService
interface MyLspService : RpcService {
    @KsMethod("textDocument/didOpen")
    @KsNotification
    suspend fun didOpen(params: DidOpenParams)

    @KsMethod("textDocument/completion")
    suspend fun completion(params: CompletionParams): CompletionList
}
```

## Transport semantics

- Returns a `SingleChannelConnection<String>` (no sub-services)
- Bidirectional: both sides can host a service and call the other
- The `@KsMethod` path maps to the JSON-RPC `method` field
- Request/response correlation uses the JSON-RPC `id` field
- Binary data is not supported (JSON-only wire format)

## Error mapping

JSON-RPC errors use the standard `error` object with `code`, `message`, and optional `data` fields. ksrpc maps `@KsError` codes to JSON-RPC error codes. See the [error handling guide](error-handling.md) for details on defining custom error types.

## Context propagation

`@KsContext` bindings in JSON-RPC default to the `RootSiblings` convention, where context values are added as sibling keys at the root of the params object. Other conventions are available. See the [context propagation guide](context-propagation.md) for details.

## See also

- [Error handling](error-handling.md) -- `@KsError` and error code mapping
- [Context propagation](context-propagation.md) -- `@KsContext` conventions
- [Socket transport](transport-sockets.md) -- the underlying byte stream protocol
- [Transports overview](transports.md) -- comparison of all transports
