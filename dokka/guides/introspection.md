# Module introspection

# Introspection

The `ksrpc-introspection` module lets clients discover service metadata at runtime -- service names, endpoint lists, and input/output type schemas.

## Setup

Add the introspection dependency:

```kotlin
implementation("com.monkopedia.ksrpc:ksrpc-introspection:$KSRPC_VERSION")
```

## Opting in

Annotate your service with [`@KsIntrospectable`](https://monkopedia.github.io/ksrpc/ksrpc-introspection/com.monkopedia.ksrpc.annotation/-ks-introspectable/index.html) and extend [`IntrospectableRpcService`](https://monkopedia.github.io/ksrpc/ksrpc-introspection/com.monkopedia.ksrpc/-introspectable-rpc-service/index.html) instead of [`RpcService`](https://monkopedia.github.io/ksrpc/ksrpc-api/com.monkopedia.ksrpc/-rpc-service/index.html):

```kotlin
import com.monkopedia.ksrpc.IntrospectableRpcService
import com.monkopedia.ksrpc.annotation.KsIntrospectable
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService

@KsService
@KsIntrospectable
interface MyService : IntrospectableRpcService {
    @KsMethod("/greet")
    suspend fun greet(name: String): String

    @KsMethod("/compute")
    suspend fun compute(input: ComputeRequest): ComputeResult
}
```

The compiler plugin generates the introspection endpoint automatically. Every `IntrospectableRpcService` exposes a `getIntrospection()` method that clients can call.

## Querying introspection

```kotlin
val service: MyService = channel.toStub<MyService>()
val introspection = service.getIntrospection()

// Service name (fully qualified)
val name = introspection.getServiceName()

// List of endpoint names
val endpoints = introspection.getEndpoints()
// e.g. ["greet", "compute"]

// Detailed info for a specific endpoint
val info = introspection.getEndpointInfo("greet")
// RpcEndpointInfo(endpoint = "greet", input = DataStructure(...), output = DataStructure(...))
```

## IntrospectionService

The [`IntrospectionService`](https://monkopedia.github.io/ksrpc/ksrpc-introspection/com.monkopedia.ksrpc/-introspection-service/index.html) returned by `getIntrospection()` is itself a `@KsService` with these methods:

| Method | Description |
|--------|-------------|
| `getServiceName()` | Returns the fully qualified service name |
| `getEndpoints()` | Returns the list of endpoint names |
| `getEndpointInfo(endpoint)` | Returns [`RpcEndpointInfo`](https://monkopedia.github.io/ksrpc/ksrpc-introspection/com.monkopedia.ksrpc/-rpc-endpoint-info/index.html) with input/output type metadata |
| `getIntrospectionFor(service)` | Returns introspection for a referenced sub-service |

## RpcEndpointInfo and RpcDataType

Each endpoint's input and output are described by an [`RpcDataType`](https://monkopedia.github.io/ksrpc/ksrpc-introspection/com.monkopedia.ksrpc/-rpc-data-type/index.html):

- **`RpcDataType.DataStructure`** -- a serializable type. Contains an `RpcDescriptor` tree describing the structure: `dataType` (enum like `STRING`, `INT`, `CLASS`, `LIST`, etc.), `serialName`, `elements` (child fields), and an optional `id` for recursive types.
- **`RpcDataType.BinaryData`** -- a binary payload ([`RpcBinaryData`](https://monkopedia.github.io/ksrpc/ksrpc-core/com.monkopedia.ksrpc.channels/-rpc-binary-data/index.html)).
- **`RpcDataType.Service`** -- a sub-service reference. The `qualifiedName` identifies the service, and you can call `getIntrospectionFor(qualifiedName)` to inspect it.

## Inspecting sub-services

When an endpoint returns or accepts a sub-service, its `RpcDataType.Service` carries the service name. Use `getIntrospectionFor` to recurse into it:

```kotlin
val info = introspection.getEndpointInfo("getEntity")
val outputType = info.output
if (outputType is RpcDataType.Service) {
    val subIntrospection = introspection.getIntrospectionFor(outputType.qualifiedName)
    val subEndpoints = subIntrospection.getEndpoints()
}
```

This also works for `Flow<T>` endpoints, which are backed by [`KsFlowService`](https://monkopedia.github.io/ksrpc/ksrpc-flow/com.monkopedia.ksrpc.flow/-ks-flow-service/index.html)`<T>` sub-services under the hood.

## RpcDescriptor structure

For `DataStructure` types, the `RpcDescriptor` tree mirrors the `kotlinx.serialization` descriptor:

```kotlin
val info = introspection.getEndpointInfo("compute")
val inputSchema = (info.input as RpcDataType.DataStructure).schema
// RpcDescriptor(
//   dataType = CLASS,
//   serialName = "com.example.ComputeRequest",
//   elements = { "field1" -> RpcDescriptor(dataType = STRING, ...), ... }
// )
```

Recursive types are handled via the `id` field -- only the first occurrence has `elements` populated; subsequent occurrences reference the same `id` with an empty `elements` map.

## Related guides

- [Service Declaration](service-declaration.md) -- defining `@KsService` interfaces that can be introspected
- [Flow Streaming](flow-streaming.md) -- flow endpoints appear as `KsFlowService<T>` sub-services in introspection
- [Getting Started](getting-started.md) -- project setup and dependencies
