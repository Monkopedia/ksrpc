# Module transport-jni

# JNI Transport

The JNI transport provides a shared RPC interface across the JVM/Native boundary in the same process. It uses binary serialization rather than JSON, eliminating network overhead entirely.

## Module and dependencies

```kotlin
implementation("com.monkopedia.ksrpc:ksrpc-jni:$KSRPC_VERSION")
```

## Platform availability

| Side | Platform | Notes |
|------|----------|-------|
| JVM | JVM | `JniConnection` -- the JVM side of the bridge |
| Native | Kotlin/Native | `NativeConnection` -- the native side of the bridge |

Both sides run in the same OS process, communicating through JNI calls rather than sockets or HTTP.

## Native side: hosting a service

ksrpc owns all of the JNI export plumbing, so hosting a service inside a Kotlin/Native `.so` is a single `JNI_OnLoad` export that hands ksrpc the environment configuration and a registration lambda.

1. **Build your native module as a shared library** that exports `ksrpc-jni`, so its klib (and the JNI exports ksrpc owns) link into your `.so`:

   ```kotlin
   kotlin {
       // ... your native target ...
       binaries {
           sharedLib {
               export(project(":ksrpc-jni"))
           }
       }
   }
   ```

2. **Add one `JNI_OnLoad` export** that calls `ksrpcNativeHost`. You write no `@CName("Java_...")` functions and no JVM-side `external fun`s -- ksrpc resolves those by JNI name mangling against the symbols its klib contributes to your `.so`:

   ```kotlin
   import com.monkopedia.jni.JavaVMVar
   import com.monkopedia.jni.jint
   import com.monkopedia.ksrpc.channels.registerDefault
   import com.monkopedia.ksrpc.jni.ksrpcNativeHost
   import kotlinx.cinterop.COpaquePointer
   import kotlinx.cinterop.CPointer

   @CName("JNI_OnLoad")
   fun jniOnLoad(vm: CPointer<JavaVMVar>, reserved: COpaquePointer?): jint =
       ksrpcNativeHost { env, connection ->
           connection.registerDefault(MyServiceImpl(env))
       }
   ```

   `ksrpcNativeHost` only **stores** this configuration at load time -- nothing is registered yet. The `register` lambda runs **once per JVM connection**: each time the JVM opens a connection, ksrpc builds a fresh [`KsrpcEnvironment`](https://monkopedia.github.io/ksrpc/ksrpc-core/com.monkopedia.ksrpc/-ksrpc-environment/index.html) (from your `configure` block) and a new [`Connection`](https://monkopedia.github.io/ksrpc/ksrpc-core/com.monkopedia.ksrpc.channels/-connection/index.html), then runs `register` against them on the calling JVM thread. So **each connection is independent** -- it gets its own environment and its own service instance(s); `MyServiceImpl(env)` above is constructed per connection, not shared across clients.

`ksrpcNativeHost` also takes an optional `configure` block, applied to each connection's environment. The whole host API is typed on `JniSerialized`, so `JniSerialization` is pre-set and a mismatched serializer is a compile error:

```kotlin
ksrpcNativeHost(
    configure = { errorListener = ErrorListener { it.printStackTrace() } }
) { env, connection ->
    connection.registerDefault(MyServiceImpl(env))
}
```

> `JNI_OnLoad`'s `JavaVM*` / `reserved` arguments are part of the JNI ABI but are not used by ksrpc: the dispatcher is JVM-thread-driven, so there is no native-initiated thread to attach. `ksrpcNativeHost` ignores them and simply returns the JNI version for the export to return.

## JVM side: loading and connecting

Load the shared library once (e.g. via `NativeUtils.loadLibraryFromJar`), then open a connection. `KsrpcNativeHost.connect` performs the three steps of bringing up a connection in one call -- it **creates** the connection (with a fresh per-connection native environment), **drives the native registration** so the host's `register` lambda runs against *this* connection, and hands it back ready to **use**:

```kotlin
import com.monkopedia.ksrpc.jni.JniSerialized
import com.monkopedia.ksrpc.jni.KsrpcNativeHost
import com.monkopedia.ksrpc.jni.NativeUtils
import com.monkopedia.ksrpc.toStub

// Load the Kotlin/Native shared library bundled on the classpath (once per process)
NativeUtils.loadLibraryFromJar("/libs/libmy_service.so")

// create a connection (fresh native environment) AND register the native service on it
val connection = KsrpcNativeHost.connect(scope)

// use this connection
val service = connection.defaultChannel().toStub<MyService, JniSerialized>()
val result = service.someMethod("hello")
```

The native `register` lambda runs as part of `connect`, on this thread, so the service is hosted on the returned connection before `connect` returns. **Each `connect` call is independent**: it gets its own native environment and its own service instance(s) -- if you call `connect` again you get a separate connection with a separate host-side service, not a shared one.

`KsrpcNativeHost.connect` defaults to a `JniSerialization` environment; pass your own `KsrpcEnvironment<JniSerialized>` to customize the JVM-side logger or error listener. The lower-level primitives (`JniConnection`, `JniSerialization`, `NativeUtils`) remain public if you need finer control over the create/register/use steps yourself.

The working end-to-end reference is the test harness: [`ksrpc-test/src/nativeMain/kotlin/Test.kt`](https://github.com/Monkopedia/ksrpc/blob/main/ksrpc-test/src/nativeMain/kotlin/Test.kt) (the `JNI_OnLoad`) and [`ksrpc-test/src/jvmTest/kotlin/JniTest.kt`](https://github.com/Monkopedia/ksrpc/blob/main/ksrpc-test/src/jvmTest/kotlin/JniTest.kt) (the `KsrpcNativeHost.connect` calls).

## Transport semantics

- Uses `JniSerialized` as the wire format -- a binary serialization format, not JSON
- Bidirectional at the protocol level: both JVM and Native sides can host and call services
- Full [`Connection`](https://monkopedia.github.io/ksrpc/ksrpc-core/com.monkopedia.ksrpc.channels/-connection/index.html) with sub-service support in both directions
- Zero network overhead -- data passes through JNI method calls in the same process
- Packet-based protocol using the same `PacketChannelBase` as other bidirectional transports
- Coroutine continuations are bridged across the JNI boundary, preserving suspend semantics

## See also

- [Bidirectional communication](bidirectional.md) -- two-way RPC patterns
- [Transports overview](transports.md) -- comparison of all transports
