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

The setup of a service has a bit of boiler plate, but after that its very easy to add/modify
methods. The interface must extend RpcService, and it must have a companion that extends the
RpcObject. To extend RpcObject, a stub factory needs to be passed in, which is setup easiest
as the following.

```
interface MyService : RpcService {
    fun myRpcCall(i: MyInputSerializable): MyOutputSerializable = map("/myRpcCall", i)

    fun myRpcWithoutInput(u: Unit): MyOutputSerializable = map("/noInput", u)

    fun myRpcWithoutOutput(i: MyInputSerializable): Unit = map("/noOutput", u)

    private class MyServiceStub(val channel: RpcServiceChannel): MyService, RpcService by channel

    companion object : RpcObject<MyService>(MyService::class, ::MyServiceStub)
}
```

The map and subservice (see below) methods use inline reified types to pull the serializers
relevant to the input/output types for any mapped methods.

# Implementing services

To implement a service, one simply extends the interface and implements all of the methods
on it. Sadly there is no good way to enforce all methods are overridden in compilation, given the
default implementations that are needed for JS/Native platforms, so be sure to override every
method. To handle the binding to a channel for serving, all implementations should extend Service.

```
class MyServiceImpl : Service(), MyService {
}
    fun myRpcCall(i: MyInputSerializable): MyOutputSerializable {
        useInput(i)
        return MyOutputSerializable()
    }

    fun myRpcWithoutInput(u: Unit): MyOutputSerializable {
        return MyOutputSerializable()
    }

    fun myRpcWithoutOutput(i: MyInputSerializable): Unit {
        useInput(i)
    }
```

The companion (RpcObject) of the interface can turn the service into a channel, and then a number
of options can be used for hosting.

```
val service = MyServiceImpl()
val channel = MyService.serializedChannel(service)
// Host on stdin/stdout
channel.serveOnStd()
// Host on an input/output stream
channel.serve(inputStream, outputStream)
// Host on HTTP with Ktor
embeddedServer {
    ...
    routing {
        serve("/my_service", channel)
    }
}
```

# Connecting

Each platform provides a form of KsrpcUri.connect() which has different capabilities. If possible,
it will create a serialized channel, which can be wrapped into a service to make calls on.

```
val uri = "http://localhost:8080/my_service".toKsrpcUri()
val service = MyService.wrap(uri.connect())

val output = service.mRpcCall(MyInputSerializable())
```
