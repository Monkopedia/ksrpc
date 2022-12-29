# Kotlin Simple RPCs

[![GitHub license](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat)](http://www.apache.org/licenses/LICENSE-2.0)
[![Kotlin](https://img.shields.io/badge/kotlin-1.8.0-blue.svg?logo=kotlin)](http://kotlinlang.org)
[![Maven Central](https://img.shields.io/maven-central/v/com.monkopedia.ksrpc/ksrpc-core/0.7.0)](https://search.maven.org/artifact/com.monkopedia.ksrpc/ksrpc-core/0.7.0/pom)
[![KDoc link](https://img.shields.io/badge/API_reference-KDoc-blue)](https://monkopedia.github.io/ksrpc/ksrpc/)

This is a simple library that allows for json-like RPCs with a simple service declaration in kotlin
common. Currently, hosting is mostly only supported in the JVM, but clients can be from
JVM/JS/Native as needed.

## Why not protobuf or one of the 1000 other RPC projects?

Because those didn't quite exactly meet my needs. Here are a few of the things I considered when
deciding to write this.

 - Wanted a json or similar transport
 - Wanted to curl at it for testing
 - Wanted to connect directly through input/output streams
 - Wanted to also be able to run a process and read stdin/out like LSP (and other) protocols do
 - Wanted to support most or all Kotlin platforms (at least as client)

The result after a little work was ksrpc. Its not perfect, but it fits my situation well. It
has a relatively simple way to declare services and supports a number of connection mechanisms
depending on the platform being targeted.

 - HTTP (JVM, Native, JS (Client only))
 - Socket (JVM, Native)
 - Stdin/out (JVM, Native)
 - Local class instantiation (JVM)
 - Web sockets (JVM, Native, JS (Client only))
 - jsonrpc 2.0 (JVM, Native\*)

 \* Not implemented but expected soon

# Build setup

Depending on ksrpc requires adding the gradle plugin to apply the compiler plugin element, as well
as depending on the runtime library.

```kotlin
plugins {
    `java`
    ...
    id("com.monkopedia.ksrpc.plugin") version "0.7.0"
}

dependencies {
    ...
    implementation("com.monkopedia:ksrpc-core:0.7.0")
}
```

# Service declaration

KSRPC uses annotations to tag services and provide information about how to uniquely map methods.
The compiler plugin then generates a stub implementation and companion object to serve as adapters
for the service which use kotlinx serialization and the unique name to perform the RPCs over a variety
of communication mechanisms.

All KSRPC services start with an interface that extends [RpcService](https://monkopedia.github.io/ksrpc/ksrpc/com.monkopedia.ksrpc/-rpc-service/index.html)
(for API access) and are annotated with [KsService](https://monkopedia.github.io/ksrpc/ksrpc/com.monkopedia.ksrpc.annotation/-ks-service/index.html)
(to make it easier for the compiler plugin). Methods tagged with [KsMethod](https://monkopedia.github.io/ksrpc/ksrpc/com.monkopedia.ksrpc.annotation/-ks-method/index.html)
get adapters/stubs generated for them by the compiler, and any non-tagged methods will spit out compiler warnings.

```kotlin
@KsService
interface MyService : RpcService {
    @KsMethod("/consistent_name")
    suspend fun myRpcMethod(str: String): Int
}
```

## Primitive types

Any primitive types that are supported by kotlinx serialization can be used directly as inputs or
outputs for methods.

```kotlin
@KsService
interface MyService : RpcService {
    @KsMethod("/userId")
    suspend fun getUserId(userString: String): Int
}
```

## Unit

Since all methods must have an input and an output, Unit is used to indicate void.

```kotlin
@KsService
interface MyService : RpcService {
    @KsMethod("/noInput")
    suspend fun myRpcWithoutInput(u: Unit): MyOutputSerializable

    @KsMethod("/noOutput")
    suspend fun myRpcWithoutOutput(i: MyInputSerializable)
}
```

## Serializable types

Any serializable class can be used as an input or output to [KsMethods](https://monkopedia.github.io/ksrpc/ksrpc/com.monkopedia.ksrpc.annotation/-ks-method/index.html).

```kotlin
@Serializable
data class MyInputSerializable(
    val str: String,
    val i: Int?
)

@Serializable
data class MyOutputSerializable(
    val data: String
)

@KsService
interface MyService : RpcService {
    @KsMethod("/myRpcCall")
    suspend fun myRpcCall(i: MyInputSerializable): MyOutputSerializable
}
```

## Binary data

Binary data is supported for inputs and outputs on some channels. However its worth noting that
streaming is only supported for ktor http and websockets. For sockets, the data is consumed and
transferred together, and binary calls are not supported on jsonrpc.

```kotlin
@KsService
interface MyService : RpcService {
    @KsMethod("/binaryInput")
    suspend fun writeBinaryData(data: ByteReadChannel): String

    @KsMethod("/binaryOutput")
    suspend fun readBinaryData(key: String): ByteReadChannel
}
```

## Sub-services

Sub-services provide a way to pass other [KsServices](https://monkopedia.github.io/ksrpc/ksrpc/com.monkopedia.ksrpc.annotation/-ks-service/index.html)
as input or output to a [KsMethod](https://monkopedia.github.io/ksrpc/ksrpc/com.monkopedia.ksrpc.annotation/-ks-method/index.html).
Note that they can only be called as input on channels that are a ChannelClient such as a [Connection](https://monkopedia.github.io/ksrpc/ksrpc/com.monkopedia.ksrpc.channels/-connection/index.html),
and returning services can only happen on channels that are a ChannelHost such as when hosting on HTTP
or with a [Connection](https://monkopedia.github.io/ksrpc/ksrpc/com.monkopedia.ksrpc.channels/-connection/index.html).

```kotlin
@KsService
interface MyEntity : RpcService {
    @KsMethod("/content")
    suspend fun fetchContent(u: Unit): ByteReadChannel

    @KsMethod("/name")
    suspend fun getName(u: Unit): String

    @KsMethod("/id")
    suspend fun getId(u: Unit): Int
}

@KsService
interface MyService : RpcService {
    // Sub-service as an output.
    @KsMethod("/get")
    suspend fun getEntity(id: Int): MyEntity
    // Subservice as an input.
    @KsMethod("/create")
    suspend fun createEntity(entity: MyEntity): Int
}
```

# Implementing services

To implement a service, one simply extends the interface and implements all of the methods
on it.

```kotlin
class MyServiceImpl : MyService {
    override suspend fun myRpcCall(i: MyInputSerializable): MyOutputSerializable {
        useInput(i)
        return MyOutputSerializable()
    }

    override suspend fun myRpcWithoutInput(u: Unit): MyOutputSerializable {
        return MyOutputSerializable()
    }

    override suspend fun myRpcWithoutOutput(i: MyInputSerializable) {
        useInput(i)
    }
    
    override suspend fun myUserService(userId: String): MyUserService {
        return MyUserServiceImpl(userId)
    }

    override suspend fun writeBinaryData(data: ByteReadChannel): String {
        val key = generateKey()
        someDataStore[key] = data.readRemaining()
        return key
    }

    override suspend fun readBinaryData(key: String): ByteReadChannel {
        return ByteReadChannel(someDataStore[key])
    }
}
```

The companion (RpcObject) of the interface can turn the service into a channel or a channel into a service stub
implementation, and then a number of options can be used for hosting.

# Environment setup

All the channels and services share a KsrpcEnvironment object that can be built with the
ksrpcEnvironment method. The object holds the default coroutine scope used, the
serialization format, and an error handler.

```kotlin
// Construct a KsrpcEnvironment for use in hosting.
val env = ksrpcEnvironment {
    serialization = Json {
        encodeDefaults = true
    }
    defaultScope = myDefaultJob
    errorListener = ErrorListener { t ->
        t.printStackTrace()
    }
}
```

Error handlers for individual channels/services can be customized by making a local version of the
environment with a different handler.

```kotlin
val localEnv = env.onError { t ->
    t.printStackTrace()
    errorCount++
}
```

# Hosting (JVM)

## HTTP (ksrpc-ktor-client, ksrpc-ktor-server)

Hosting on HTTP is integrated with ktor, a base url is provided both on the client and server and
all RPCs run on sub-paths using POSTs, and the content is encoded as json.

```kotlin
val env = ksrpcEnvironment { }
val service = MyServiceImpl()
// Host on HTTP with Ktor
embeddedServer {
    ...
    routing {
        serve("/my_service", service, env)
    }
}
```

## Web sockets (ksrpc-ktor-websocket-client, ksrpc-ktor-websocket-server)

Serving websockets attaches pretty much the same way as HTTP, except a different method. The
communication happens with a custom protocol over websocket packets, sending some header
information, followed by the json encoded content.

```kotlin
val env = ksrpcEnvironment { }
val service = MyServiceImpl()
// Host on HTTP with Ktor
embeddedServer {
    ...
    routing {
        serveWebsocket("/ws_my_service", service, env)
    }
}
```


## Socket (ksrpc-sockets)

Given an input and output stream (from a socket or otherwise), a [Connection](https://monkopedia.github.io/ksrpc/ksrpc/com.monkopedia.ksrpc.channels/-connection/index.html) can be created, and
then a service hosted on it. When communication goes over input/output streams, a Content-Length is
sent in http header format, followed by the content encoded in json.

```kotlin
val serverSocket = ServerSocket(1234)
val env = ksrpcEnvironment { }
val service = MyServiceImpl()
val hostingContext = newFixedThreadPoolContext(3, "Hosting context")

while (true) {
    val socket = serverSocket.accept()
    GlobalScope.launch(hostingContext) {
        val connection = (socket.getInputStream() to socket.getOutputStream())
            .asConnection(env)
        connection.registerDefault(service)
    }
}
```

## Std in/out (ksrpc-sockets)

A convenience method is provided to do the same kind of hosting as with Sockets.

```kotlin
val env = ksrpcEnvironment { }
val service = MyServiceImpl()
val connection = stdInConnection(env)
connection.registerDefault(service)
```

## jsonrpc 2.0 (ksrpc-jsonrpc)

As of 0.5.2, jsonrpc 2.0 is functional in ksrpc. This is supported on a socket or std in/out, with
similar methods to connect them. The name from the [KsMethod](https://monkopedia.github.io/ksrpc/ksrpc-core/com.monkopedia.ksrpc.annotation/-ks-method/index.html) annotation is translated to the jsonrpc
method field.

```kotlin
val env = ksrpcEnvironment { }
val service = MyServiceImpl()
val connection = stdInJsonRpcConnection(env)
connection.registerDefault(service)
```

By default, all methods that return Unit will be interpretted as notifications for the jsonrpc
protocol. Until more support is added, use JsonElement? for requests with no response data.

```kotlin
@KsService
interface MyService : RpcService {
    @KsMethod("/aMethod")
    suspend fun aMethod(u: Unit): JsonElement? // void request
}
```

# Connecting

Each protocol provides different mechanisms for creating a connection or a channel depending on
the current platforms capabilities. Those can then by turned into the hosted API service using
`toStub()'.

```kotlin
val env = ksrpcEnvironment { }
val connection = HttpClient { }.asConnection("http://localhost:8080/my_service", env)
val service = connection.defaultChannel().toStub<MyService>()

val output = service.mRpcCall(MyInputSerializable())
```

Client side or bidirectional connection methods:

 - [HttpClient.asConnection(baseUrl, env)](https://monkopedia.github.io/ksrpc/ksrpc-ktor-client/com.monkopedia.ksrpc.ktor/as-connection.html)
 - [HttpClient.asWebsocketConnection(baseUrl, env)](https://monkopedia.github.io/ksrpc/ksrpc-ktor-websocket-client/com.monkopedia.ksrpc.ktor.websocket/as-websocket-connection.html)
 - [Pair<ByteReadChannel, ByteWriteChannel>.asConnection(env)](https://monkopedia.github.io/ksrpc/ksrpc-sockets/com.monkopedia.ksrpc.sockets/as-connection.html)
 - [Pair<InputStream, OutputStream>.asConnection(env)](https://monkopedia.github.io/ksrpc/ksrpc-sockets/com.monkopedia.ksrpc.sockets/as-connection.html)
 - [ProcessBuilder.asConnection(env)](https://monkopedia.github.io/ksrpc/ksrpc-sockets/com.monkopedia.ksrpc.sockets/as-connection.html)
 - [Pair<ByteReadChannel, ByteWriteChannel>.asJsonRpcConnection(env)](https://monkopedia.github.io/ksrpc/ksrpc-jsonrpc/com.monkopedia.ksrpc.jsonrpc/as-json-rpc-connection.html)

Server side hosting methods:
 - [Routing.serve(basePath, service, env)](https://monkopedia.github.io/ksrpc/ksrpc-ktor-server/com.monkopedia.ksrpc.ktor/serve.html)
 - [Routing.serveWebsocket(basePath, service, env)[https://monkopedia.github.io/ksrpc/ksrpc-ktor-websocket-server/com.monkopedia.ksrpc.ktor.websocket/serve-websocket.html]
 - [withStdInOut(env, withConnection)](https://monkopedia.github.io/ksrpc/ksrpc-sockets/com.monkopedia.ksrpc.sockets/with-std-in-out.html)
 - [ServiceApp](https://monkopedia.github.io/ksrpc/ksrpc-server/com.monkopedia.ksrpc.server/-service-app/index.html)
 - [stdInJsonRpcConnection(env)](https://monkopedia.github.io/ksrpc/ksrpc-jsonrpc/com.monkopedia.ksrpc.jsonrpc/std-in-json-rpc-connection.html)


## [Connection](https://monkopedia.github.io/ksrpc/ksrpc-core/com.monkopedia.ksrpc.channels/-connection/index.html) / [SingleChannelConnection](https://monkopedia.github.io/ksrpc/ksrpc-core/com.monkopedia.ksrpc.channels/-single-channel-connection/index.html)

For bidirectional communication channels (see section on bidirectional channels below), a default
channel can be used to connect to it.

```kotlin
val env = ksrpcEnvironment { }
val connection = stdInConnection(env)
val service = connection.defaultChannel().toStub<MyService>()

val output = service.mRpcCall(MyInputSerializable())
```

# Bidirectional communication

When communicating on a socket, websocket or jsonrpc, calls can happen in both directions. Allowing both a hosted
service that receives incoming calls and a client that sends outgoing calls.

## [SingleChannelConnection](https://monkopedia.github.io/ksrpc/ksrpc-core/com.monkopedia.ksrpc.channels/-single-channel-connection/index.html)

For jsonrpc, a [SingleChannelConnection](https://monkopedia.github.io/ksrpc/ksrpc-core/com.monkopedia.ksrpc.channels/-single-channel-connection/index.html) is provided, which can handle one service in each direction
(no sub-services). This contains both the [registerDefault](https://monkopedia.github.io/ksrpc/ksrpc-core/com.monkopedia.ksrpc.channels/register-default.html) and [defaultChannel](https://monkopedia.github.io/ksrpc/ksrpc-core/com.monkopedia.ksrpc.channels/-single-channel-client/default-channel.html) methods referenced
above, and can use both of them at once.

```kotlin
val env = ksrpcEnvironment { }
val connection = stdInConnection(env)
val hostingService = MyServiceImpl()
connection.registerDefault(hostingService)
val clientService = connection.defaultChannel().toStub<MyService>()

val output = clientService.mRpcCall(MyInputSerializable())
```

There is also a [connect] method that handles both registering and fetching the default channel.

```kotlin
val env = ksrpcEnvironment { }
val connection = stdInConnection(env)
connection.connect<MyHostService, MyClientService> { client ->
    // client is MyClientService.
    MyServiceImpl() // Returned service gets passed into registerDefault
}
```

## [Connection](https://monkopedia.github.io/ksrpc/ksrpc-core/com.monkopedia.ksrpc.channels/-connection/index.html)

[Connections](https://monkopedia.github.io/ksrpc/ksrpc-core/com.monkopedia.ksrpc.channels/-connection/index.html) work the
same way as [SingleChannelConnections](https://monkopedia.github.io/ksrpc/ksrpc-core/com.monkopedia.ksrpc.channels/-single-channel-connection/index.html)
for setup and connection, however they support sub-services both for input and output. This way services as inputs to
methods can be used for contextual callbacks.

Here is an example of a service that has a task with parameters and a service that gets callbacks
for updates.

```kotlin
@KsService
interface MyCallbackService : RpcService {
    @KsMethod("/start")
    suspend fun onTaskStarted(u: Unit)
    @KsMethod("/progress")
    suspend fun onTaskProgress(i: Int)
    @KsMethod("/done")
    suspend fun onTaskComplete(u: Unit)
}

@Serializable
data class TaskParams(
    val inputString: String
)

@KsService
interface MyTaskService: RpcService {
    @KsMethod("/cancel")
    suspend fun cancel(u: Unit)
    @KsMethod("/start")
    suspend fun start(params: TaskParams)
}

@KsService
interface MyService : RpcService {
    @KsMethod("/work")
    suspend fun createTask(service: MyCallbackService): MyTaskService
}
```

This is how it could be used by the client.

```kotlin
val env = ksrpcEnvironment { }
val connection = stdInConnection(env)
val service = connection.defaultChannel().toStub<MyService>()
val taskName = "My task"

service.createTask(object : MyCallbackService {
    override suspend fun onTaskStarted(u: Unit) {
        println("$taskName started")
    }
    override suspend fun onTaskProgress(i: Int) {
        println("$taskName progress: $i")
    }
    override suspend fun onTaskComplete(u: Unit) {
        println("$taskName complete")
    }
}).start(TaskParams(taskName))
```

# API Docs

For further information, see the API docs, which are hosted on [monkopedia.github.io](https://monkopedia.github.io/ksrpc/).

# TODO List

Unranked list of things I know I want to implement:

- Stdin/out native methods that use the above implementation
- jsonrpc native support (much like above)
- Additional annotations and parsing in compiler plugin to have support for things like notifications not just requests
- Finish testing [TrackingService](ksrpc/src/commonMain/kotlin/TrackingService.kt) and publish it as API
- Add tests to ensure no leaks around sub-service usage and cleanup
- Support building mac/windows binaries in github release workflow
