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

   The `register` lambda receives the shared [`KsrpcEnvironment`](https://monkopedia.github.io/ksrpc/ksrpc-core/com.monkopedia.ksrpc/-ksrpc-environment/index.html) and the freshly-opened [`Connection`](https://monkopedia.github.io/ksrpc/ksrpc-core/com.monkopedia.ksrpc.channels/-connection/index.html) each time the JVM connects, running on the calling JVM thread.

`ksrpcNativeHost` also takes an optional `configure` block to tweak the shared environment. The whole host API is typed on `JniSerialized`, so `JniSerialization` is pre-set and a mismatched serializer is a compile error:

```kotlin
ksrpcNativeHost(
    configure = { errorListener = ErrorListener { it.printStackTrace() } }
) { env, connection ->
    connection.registerDefault(MyServiceImpl(env))
}
```

> `JNI_OnLoad`'s `JavaVM*` / `reserved` arguments are part of the JNI ABI but are not used by ksrpc: the dispatcher is JVM-thread-driven, so there is no native-initiated thread to attach. `ksrpcNativeHost` ignores them and simply returns the JNI version for the export to return.

## JVM side: loading and connecting

Load the shared library (e.g. via `NativeUtils.loadLibraryFromJar`), then `KsrpcNativeHost.connect` pairs the environment, connection, and native registration into one call:

```kotlin
import com.monkopedia.ksrpc.jni.JniSerialized
import com.monkopedia.ksrpc.jni.KsrpcNativeHost
import com.monkopedia.ksrpc.jni.NativeUtils
import com.monkopedia.ksrpc.toStub

// Load the Kotlin/Native shared library bundled on the classpath
NativeUtils.loadLibraryFromJar("/libs/libmy_service.so")

// Build the environment + connection and run the native `register` lambda
val connection = KsrpcNativeHost.connect(scope)

// Call a service hosted on the native side
val service = connection.defaultChannel().toStub<MyService, JniSerialized>()
val result = service.someMethod("hello")
```

`KsrpcNativeHost.connect` defaults to a `JniSerialization` environment; pass your own `KsrpcEnvironment<JniSerialized>` to customize the JVM-side logger or error listener. The lower-level primitives (`JniConnection`, `JniSerialization`, `NativeUtils`) remain public if you need finer control.

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
