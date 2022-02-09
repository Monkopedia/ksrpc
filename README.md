# Kotlin Simple RPCs

This is a simple library that allows for json-like RPCs with a simple service declaration in kotlin
common. Currently hosting is only supported in the JVM, but clients can be from JVM/JS/Native as
needed.

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

 - HTTP (JVM, JS, Native)
 - Socket (JVM, Native\*)
 - Stdin/out (JVM, Native\*)
 - Local class instantiation (JVM)
 - Web sockets (JVM, JS)

\* Not implemented but expected soon

# Service declaration

KSRPC uses annotations to tag services and provide information about how to uniquely map methods.
The compiler plugin then generates a stub implementation and companion object to serve as adapters
for the service.

```
@KsService
interface MyService : RpcService {
    @KsMethod("/myRpcCall")
    suspend fun myRpcCall(i: MyInputSerializable): MyOutputSerializable

    @KsMethod("/noInput")
    suspend fun myRpcWithoutInput(u: Unit): MyOutputSerializable

    @KsMethod("/noOutput")
    suspend fun myRpcWithoutOutput(i: MyInputSerializable)

    @KsMethod("/subService")
    suspend fun myUserService(userId: String): MyUserService // MyUserService is another RpcService

    @KsMethod("/binaryInput")
    suspend fun writeBinaryData(data: ByteReadChannel): String

    @KsMethod("/binaryOutput")
    suspend fun readBinaryData(key: String): ByteReadChannel
}
```

# Implementing services

To implement a service, one simply extends the interface and implements all of the methods
on it.

```
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

The companion (RpcObject) of the interface can turn the service into a channel, and then a number
of options can be used for hosting.

```
val env = ksrpcEnvironment { }
val service = MyServiceImpl()
// Host on stdin/stdout
val connection1 = stdInConnection(env)
connection1.registerDefault(service)
// Host on an input/output stream
val connection2 = (inputStream to outputStream).asConnection(env)
connection2.registerDefault(service)
// Host on HTTP with Ktor
embeddedServer {
    ...
    routing {
        serve("/my_service", service, env)
    }
}
```

# Connecting

Each platform provides a form of KsrpcUri.connect() which has different capabilities. If possible,
it will create a serialized channel, which can be wrapped into a service to make calls on.

```
val env = ksrpcEnvironment { }
val uri = "http://localhost:8080/my_service".toKsrpcUri()
val service = uri.connect(env).toStub<MyService>()

val output = service.mRpcCall(MyInputSerializable())
```
