# Module ksrpc-api

Annotation-only module that defines the ksrpc service contract without pulling in runtime
dependencies. Contains `@KsService`, `@KsMethod`, `@KsError`, `@KsContext`, `@KsNotification`,
and the `KsContextBinding` interface used to declare per-call context propagation.

# Package com.monkopedia.ksrpc.annotation

Annotations for declaring RPC services (`@KsService`, `@KsMethod`), typed error bindings
(`@KsError`), notification semantics (`@KsNotification`), method-level timeouts (`@KsTimeout`),
and per-call context propagation (`@KsContext`). Also includes the `@KsMethodMetadata`
meta-annotation for extending the compiler plugin with custom sibling annotations.

# Package com.monkopedia.ksrpc

Contains `KsContextBinding`, the interface that bridges a coroutine-context element to and
from the wire for `@KsContext`-annotated methods.

# Module ksrpc-core

Core runtime library for ksrpc. Provides the abstract channel and connection model, the
`RpcMethod` descriptor, `RpcObject` companion generation target, serialization plumbing,
environment configuration (`KsrpcEnvironment`), and the transport-agnostic `RpcBinaryData`
interface for binary payloads.

# Package com.monkopedia.ksrpc

Core types including `RpcObject`, `RpcMethod`, `KsrpcEnvironment`, `MethodMetadata`,
exception types, and service utilities used by generated companions and user code.

# Package com.monkopedia.ksrpc.channels

Transport-agnostic abstractions: `Connection`, `SerializedService`, `ChannelHost`,
`RpcBinaryData`, `CallData`, `RpcCallId`, `CancellationSupport`, and `WireContextMap`.
These interfaces form the contract that every transport module implements.

# Module ksrpc-packets

Wire-format packet types and the base channel implementation shared by socket, websocket,
and stdin/stdout transports. Defines `Packet`, `PacketChannelBase`, and binary-chunking
logic. Transport modules extend `PacketChannelBase` with their own send/receive primitives.

# Package com.monkopedia.ksrpc.packets.internal

Internal packet protocol: `Packet` (the wire frame), `PacketChannelBase` (multiplexed
send/receive with binary chunking and cancellation support), and the packet-level JSON
codec.

# Module ksrpc-flow

Bridges Kotlin `Flow<T>` over the ksrpc sub-service protocol. Use this module when a
service method needs to return a stream of values. The compiler plugin automatically wraps
`Flow<T>` return types in `AutoClosingFlow`; use `KsFlowService<T>` directly for
multi-collection or manual lifecycle control.

# Package com.monkopedia.ksrpc.flow

`KsFlowService`, `KsFlowCollector`, `KsCollectionToken`, `AutoClosingFlow`, and the
`FlowSubserviceTransformer` that adapts `Flow<T>` onto a sub-service channel.

# Module ksrpc-introspection

Runtime endpoint metadata and schema introspection. Services that opt in with
`@KsIntrospectable` expose an `IntrospectionService` sub-service that reports endpoint
names, input/output schemas (`RpcEndpointInfo`, `RpcDescriptor`), and nested sub-service
structure at runtime.

# Package com.monkopedia.ksrpc

`IntrospectionService`, `IntrospectableRpcService`, `RpcEndpointInfo`, `RpcDescriptor`,
`RpcDataType`, and helpers that derive schema descriptors from `KSerializer` instances.

# Package com.monkopedia.ksrpc.annotation

Contains `@KsIntrospectable`, the opt-in annotation that triggers compiler generation of
introspection metadata for a `@KsService`.

# Module ksrpc-jsonrpc

Implementation of ksrpc channels that communicate using the JSON-RPC 2.0 protocol.
Supports notification semantics for `@KsNotification`-annotated methods by omitting the
`id` field on the wire.

# Package com.monkopedia.ksrpc.jsonrpc

JSON-RPC channel implementation, request/response framing, and notification handling.

# Module ksrpc-binary-ktor

Binary data adapter that bridges ktor's `ByteReadChannel` onto the transport-agnostic
`RpcBinaryData` interface. Add this module to your classpath when you need to pass ktor
byte channels through ksrpc without `ksrpc-core` depending on ktor-io directly.

# Package com.monkopedia.ksrpc.binary.ktor

`ByteReadChannelBinaryData`, plus `ByteReadChannel.asRpcBinaryData()` and
`RpcBinaryData.asByteReadChannel()` extension functions.

# Module ksrpc-binary-kotlinx-io

Binary data adapter that bridges kotlinx-io's `Source` onto the transport-agnostic
`RpcBinaryData` interface. Add this module when your application uses kotlinx-io for I/O
and you want to pass `Source` instances through ksrpc.

# Package com.monkopedia.ksrpc.binary.kxio

`SourceBinaryData`, plus `Source.asRpcBinaryData()` and `RpcBinaryData.asSource()`
extension functions.

# Module ksrpc-binary-okio

Binary data adapter that bridges okio's `BufferedSource` onto the transport-agnostic
`RpcBinaryData` interface. Add this module when your application uses okio for I/O and
you want to pass `BufferedSource` instances through ksrpc.

# Package com.monkopedia.ksrpc.binary.okio

`BufferedSourceBinaryData`, plus `BufferedSource.asRpcBinaryData()` and
`RpcBinaryData.asBufferedSource()` extension functions.

# Module ksrpc-ktor-client

Implementation of ksrpc channels that uses a ktor `HttpClient` to communicate over HTTP.
Sends each RPC call as an HTTP request; binary payloads are streamed via ktor's byte
channel integration.

# Package com.monkopedia.ksrpc.ktor

HTTP client channel, connection factory, and error-mapping utilities for ktor-based
clients.

# Module ksrpc-ktor-server

Implementation of ksrpc channels that serves RPC endpoints via a ktor HTTP server. Install
the ksrpc routing plugin into your ktor `Application` to expose services over HTTP.

# Package com.monkopedia.ksrpc.ktor

Server-side ktor routing integration that maps incoming HTTP requests to ksrpc service
calls.

# Module ksrpc-ktor-websocket-shared

Shared `WebsocketPacketChannel` implementation used by both the websocket client and
server modules. Extends `PacketChannelBase` with ktor `DefaultWebSocketSession`
send/receive, frame serialization, and binary chunking bounded by the session's max frame
size.

# Package com.monkopedia.ksrpc.ktor.websocket.internal

`WebsocketPacketChannel`, the concrete `PacketChannelBase` subclass that sends and
receives `Packet` frames over a ktor websocket session.

# Module ksrpc-ktor-websocket-client

Implementation of ksrpc channels that uses ktor to communicate over websockets as an HTTP
client. Provides a connection factory that opens a ktor websocket session and wraps it in
a `WebsocketPacketChannel`.

# Package com.monkopedia.ksrpc.ktor.websocket

Client-side websocket connection factory and channel setup.

# Module ksrpc-ktor-websocket-server

Implementation of ksrpc channels that uses ktor to serve RPC endpoints over websockets.
Install the websocket routing plugin into your ktor `Application` to accept websocket
connections and dispatch them to ksrpc services.

# Package com.monkopedia.ksrpc.ktor.websocket

Server-side ktor websocket routing integration for ksrpc.

# Module ksrpc-sockets

Implementation of ksrpc channels that communicate over POSIX sockets or standard
input/output streams. Builds on `PacketChannelBase` from `ksrpc-packets` and provides
platform-specific read/write channel adapters for JVM, Native, and JS targets.

# Package com.monkopedia.ksrpc.sockets

Public API: `ReadWriteChannel` factory functions, stdin/stdout stream helpers, and
platform adapters for JVM `InputStream`/`OutputStream`, native POSIX file descriptors,
and JS process streams.

# Module ksrpc-server

Utility module that wraps a ksrpc service into a standalone server application with
command-line configuration. Extend `BaseServiceApp` (or `ServiceApp` on JVM/Native) to
get argument parsing, transport selection, and hosting out of the box.

# Package com.monkopedia.ksrpc.server

`BaseServiceApp`, `ServiceApp`, and supporting types for building self-hosting ksrpc
server executables.

# Module ksrpc-service-worker

Experimental JS/WasmJS transport that communicates over browser service workers. Use
`createServiceWorkerWithConnection` to obtain a `Connection<String>` backed by a
registered service worker script. Requires `@OptIn(ExperimentalKsrpc::class)`.

# Package com.monkopedia.ksrpc.webworker

`createServiceWorkerWithConnection` and platform-specific service worker connection
implementations for JS and WasmJS targets.

# Module ksrpc-jni

JNI bridge for Kotlin/Native to JVM interop. Provides a compact binary serialization
format (`JniSer`/`JniSerialized`) and a `NativeConnection` that passes ksrpc calls
across the JNI boundary without JSON round-tripping. Use this module when embedding a
Kotlin/Native ksrpc service inside a JVM host (or vice versa) via shared libraries.

# Package com.monkopedia.ksrpc.jni

`JniSerialization`, `JniSer`, `JniSerialized`, `JniEncoder`/`JniDecoder`, type converters,
and the `NativeConnection` that dispatches ksrpc calls across JNI.
