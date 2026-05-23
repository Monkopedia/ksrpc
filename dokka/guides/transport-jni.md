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

## JVM side: loading and connecting

The JVM-consumer path is the clean, public part of this transport. Load the native shared library, build a `JniConnection` against a native ksrpc environment pointer, and obtain a stub:

```kotlin
import com.monkopedia.ksrpc.jni.JniConnection
import com.monkopedia.ksrpc.jni.JniSerialization
import com.monkopedia.ksrpc.jni.JniSerialized
import com.monkopedia.ksrpc.jni.NativeUtils
import com.monkopedia.ksrpc.ksrpcEnvironment
import com.monkopedia.ksrpc.toStub

// Load the Kotlin/Native shared library bundled on the classpath
NativeUtils.loadLibraryFromJar("/libs/libmy_service.so")

val env = ksrpcEnvironment(JniSerialization()) { }

// `nativeEnvironment` is a pointer returned from a native export (see below)
val nativeEnvironment: Long = MyNativeHost().createEnv()
val connection = JniConnection(scope, env, nativeEnvironment)

// Call a service hosted on the native side
val service = connection.defaultChannel().toStub<MyService, JniSerialized>()
val result = service.someMethod("hello")
```

`NativeUtils.loadLibraryFromJar`, `JniConnection`, and `JniSerialization` are public API. The native environment pointer (`createEnv` above) and any service registration are produced by the native module's own JNI exports, which you write yourself -- see the next section.

## Native side: hosting a service

> **Heads up:** there is no turnkey public API for hosting a ksrpc service inside a Kotlin/Native `.so` in 1.0.0. Doing so today means hand-writing the JNI export boilerplate that bridges the JVM `JniConnection` to a `NativeConnection`. A clean, supported host API is planned -- see [issue #204](https://github.com/Monkopedia/ksrpc/issues/204).

To host a native service today, mirror the working reference pattern in the test harness: [`ksrpc-test/src/nativeMain/kotlin/Test.kt`](https://github.com/Monkopedia/ksrpc/blob/main/ksrpc-test/src/nativeMain/kotlin/Test.kt) together with its JVM-side `external` declarations in [`ksrpc-test/src/jvmTest/kotlin/Test.kt`](https://github.com/Monkopedia/ksrpc/blob/main/ksrpc-test/src/jvmTest/kotlin/Test.kt).

The shape of the manual path is:

1. **Build your native module as a shared library**, so the JVM can load it:

   ```kotlin
   kotlin {
       // ... your native target ...
       binaries {
           sharedLib { }
       }
   }
   ```

2. **Declare JVM-side `external` functions** that the native library will implement -- a `createEnv` that returns the native ksrpc environment pointer, and a `registerService` that wires up your service:

   ```kotlin
   class MyNativeHost {
       external fun createEnv(): Long
       external fun registerService(
           connection: JniConnection,
           output: JavaJniContinuation<Int>
       )
   }
   ```

3. **Implement those exports in Kotlin/Native** with `@CName("Java_...")` functions. Each entry point initializes the JNI thread, then uses `NativeConnection` plus `registerDefault` to host the service:

   ```kotlin
   @CName("Java_com_example_MyNativeHost_registerService")
   fun registerService(env: CPointer<JNIEnvVar>, clazz: jobject, input: jobject, output: jobject) {
       // initialize the JNI thread, convert `input` to a NativeConnection,
       // then on the dispatcher:
       //   nativeConnection.registerDefault(MyNativeServiceImpl().serialized(nativeConnection.env))
   }
   ```

The dispatcher and thread-bootstrap helpers used by the reference harness currently live under the test-scoped `com.monkopedia.jnitest.*` package (`JNIDispatcher`, `initThread`, `threadEnv`). These are **not** a supported consumer API and may move; treat `ksrpc-test`'s native `Test.kt` as the authoritative example of what the manual path requires rather than copying those imports verbatim. Issue #204 tracks promoting this machinery to a stable, public host helper.

## Transport semantics

- Uses `JniSerialized` as the wire format -- a binary serialization format, not JSON
- Bidirectional at the protocol level: both JVM and Native sides can host and call services, though hosting on the native side currently requires the manual JNI export boilerplate described above
- Full [`Connection`](https://monkopedia.github.io/ksrpc/ksrpc-core/com.monkopedia.ksrpc.channels/-connection/index.html) with sub-service support in both directions
- Zero network overhead -- data passes through JNI method calls in the same process
- Packet-based protocol using the same `PacketChannelBase` as other bidirectional transports
- Coroutine continuations are bridged across the JNI boundary, preserving suspend semantics

## See also

- [Bidirectional communication](bidirectional.md) -- two-way RPC patterns
- [Transports overview](transports.md) -- comparison of all transports
