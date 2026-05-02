# Module transports

# Transports

## Overview

ksrpc supports multiple transports, each suited to different platforms, protocol requirements, and communication patterns. All transports use JSON as the default wire format via `kotlinx.serialization`, except JNI which uses a binary serialization format.

| Transport | Module(s) | Platforms | Bidirectional | Sub-services | Binary |
|-----------|-----------|-----------|---------------|--------------|--------|
| [HTTP](transport-http.md) | `ksrpc-ktor-client` / `ksrpc-ktor-server` | JVM, Native, JS/WASM (client) | No | Output only | Yes (streaming) |
| [WebSocket](transport-websocket.md) | `ksrpc-ktor-websocket-*` | JVM, Native, JS/WASM (client) | Yes | Yes | Yes (streaming) |
| [Sockets / Stdin](transport-sockets.md) | `ksrpc-sockets` | JVM, POSIX Native | Yes | Yes | Yes (buffered) |
| [JSON-RPC 2.0](transport-jsonrpc.md) | `ksrpc-jsonrpc` | JVM, POSIX Native | Yes | No | No |
| [JNI](transport-jni.md) | `ksrpc-jni` | JVM + Kotlin/Native | Yes | Yes | Binary (not JSON) |
| [Service Worker](transport-service-worker.md) | `ksrpc-service-worker` | JS, WASM (experimental) | Yes | Yes | Yes |

## Choosing a transport

**HTTP** is the simplest option for request/response workloads. It integrates with ktor's routing and works on all Kotlin targets (server is JVM/Native only). Use it when you do not need the server to push data to the client.

**WebSocket** adds bidirectional communication on top of ktor. Both sides can host services and invoke each other. Choose this for interactive or event-driven APIs that need push notifications or callbacks.

**Sockets / Stdin** use a content-length-prefixed packet protocol over raw byte streams. This is ideal for subprocess communication (LSP-style) or direct TCP connections without an HTTP stack. Supports JVM and POSIX Native.

**JSON-RPC 2.0** implements the standard JSON-RPC 2.0 protocol, making ksrpc services interoperable with non-Kotlin JSON-RPC clients and servers. Returns a [`SingleChannelConnection`](https://monkopedia.github.io/ksrpc/ksrpc-core/com.monkopedia.ksrpc.channels/-single-channel-connection/index.html) (no sub-services). Supports [`@KsNotification`](https://monkopedia.github.io/ksrpc/ksrpc-api/com.monkopedia.ksrpc.annotation/-ks-notification/index.html) for fire-and-forget messages.

**JNI** bridges Kotlin/JVM and Kotlin/Native in the same process via JNI, using binary serialization with zero network overhead. Use it to embed a Kotlin/Native ksrpc service in a JVM host (or vice versa) via shared libraries.

**Service Worker** (experimental) hosts a ksrpc service inside a browser service worker for JS and WASM targets. Requires `@OptIn(`[`ExperimentalKsrpc`](https://monkopedia.github.io/ksrpc/ksrpc-api/com.monkopedia.ksrpc.annotation/-experimental-ksrpc/index.html)`::class)`.

## See also

- [Bidirectional communication](bidirectional.md) -- patterns for two-way RPC
- [Error handling](error-handling.md) -- `@KsError` and transport-specific error mapping
- [Context propagation](context-propagation.md) -- `@KsContext` across transports
- [Flow streaming](flow-streaming.md) -- `Flow`-based streaming over bidirectional channels
