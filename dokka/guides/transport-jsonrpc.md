# Module transport-jsonrpc

# JSON-RPC 2.0 Transport

The JSON-RPC transport communicates using the [JSON-RPC 2.0](https://www.jsonrpc.org/specification) protocol over byte streams, enabling interop with other JSON-RPC clients and servers.

The JSON-RPC transport returns a [`SingleChannelConnection`](https://monkopedia.github.io/ksrpc/ksrpc-core/com.monkopedia.ksrpc.channels/-single-channel-connection/index.html) rather than a full [`Connection`](https://monkopedia.github.io/ksrpc/ksrpc-core/com.monkopedia.ksrpc.channels/-connection/index.html) -- it does not support sub-services. Each [`@KsMethod`](https://monkopedia.github.io/ksrpc/ksrpc-api/com.monkopedia.ksrpc.annotation/-ks-method/index.html) name maps directly to the JSON-RPC `method` field.

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

Methods annotated with [`@KsNotification`](https://monkopedia.github.io/ksrpc/ksrpc-api/com.monkopedia.ksrpc.annotation/-ks-notification/index.html) are sent as JSON-RPC notifications (no `id` field, no response expected). This is useful for fire-and-forget messages like LSP's `textDocument/didOpen`.

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

> **Path convention**: JSON-RPC method names map verbatim to the wire. The conventional style for JSON-RPC is `namespace/method` without a leading `/` (e.g., `textDocument/completion`), while other ksrpc transports typically use `/path` style (e.g., `/greet`). Choose your `@KsMethod` names to match the protocol conventions of the transport you are targeting.

## Error mapping

JSON-RPC errors use the standard `error` object with `code`, `message`, and optional `data` fields. ksrpc maps [`@KsError`](https://monkopedia.github.io/ksrpc/ksrpc-api/com.monkopedia.ksrpc.annotation/-ks-error/index.html) codes to JSON-RPC error codes. See the [error handling guide](error-handling.md) for details on defining custom error types.

## Context propagation

[`@KsContext`](https://monkopedia.github.io/ksrpc/ksrpc-api/com.monkopedia.ksrpc.annotation/-ks-context/index.html) bindings in JSON-RPC default to the `RootSiblings` convention, where context values are added as sibling keys at the root of the params object. Other conventions are available. See the [context propagation guide](context-propagation.md) for details.

## See also

- [Error handling](error-handling.md) -- `@KsError` and error code mapping
- [Context propagation](context-propagation.md) -- `@KsContext` conventions
- [Socket transport](transport-sockets.md) -- the underlying byte stream protocol
- [Transports overview](transports.md) -- comparison of all transports
