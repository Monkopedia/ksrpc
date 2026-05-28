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

To host a service inside a Kotlin/Native `.so`, declare a binding on one of your own JVM classes, implement it natively by delegating to `ksrpcHostConnection`, and open it from the JVM with `KsrpcNativeHost.connect`.

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

2. **Declare the binding on one of your own JVM classes** -- an `external fun` that `KsrpcNativeHost.connect` calls. The native `@CName` symbol is derived from the declaring class, so declaring it on your own class places that symbol under your package. The binding takes a single [`JniHostInit`](https://monkopedia.github.io/ksrpc/ksrpc-jni/com.monkopedia.ksrpc.jni/-jni-host-init/index.html), which you forward unchanged to `ksrpcHostConnection`:

   ```kotlin
   // your JVM source set:
   import com.monkopedia.ksrpc.jni.JniHostInit

   object MyNativeService {
       external fun initialize(host: JniHostInit)
   }
   ```

3. **Implement that binding natively** with a `@CName` export that forwards `host` to `ksrpcHostConnection`. The symbol matches your class from step 2:

   ```kotlin
   // your native source set:
   import com.monkopedia.jni.JNIEnvVar
   import com.monkopedia.jni.jobject
   import com.monkopedia.ksrpc.channels.registerDefault
   import com.monkopedia.ksrpc.jni.ksrpcHostConnection
   import kotlinx.cinterop.CPointer

   @CName("Java_com_example_MyNativeService_initialize")
   fun initialize(env: CPointer<JNIEnvVar>, clazz: jobject, host: jobject) =
       ksrpcHostConnection(env, host) { conn ->
           conn.registerDefault(MyServiceImpl(conn.env))
       }
   ```

   The `setup` lambda runs **once per connection**, on the JNI dispatcher, with the freshly-opened [`Connection`](https://monkopedia.github.io/ksrpc/ksrpc-core/com.monkopedia.ksrpc.channels/-connection/index.html). For each connection, `ksrpcHostConnection` builds that connection's own [`KsrpcEnvironment`](https://monkopedia.github.io/ksrpc/ksrpc-core/com.monkopedia.ksrpc/-ksrpc-environment/index.html), wraps the JVM connection, runs your `setup`, and hands the JVM a single opaque handle that backs the connection's lifecycle. So **each connection is independent** -- it gets its own environment and its own service instance(s); `MyServiceImpl(conn.env)` above is constructed per connection, not shared across clients.

`ksrpcHostConnection` also takes an optional `configure` block, applied to each connection's environment. The whole host API is typed on `JniSerialized`, so `JniSerialization` is pre-set and a mismatched serializer is a compile error:

```kotlin
ksrpcHostConnection(
    env, host,
    configure = { errorListener = ErrorListener { it.printStackTrace() } }
) { conn ->
    conn.registerDefault(MyServiceImpl(conn.env))
}
```

## JVM side: loading and connecting

Load the shared library once (e.g. via `NativeUtils.loadLibraryFromJar`), then open a connection with `KsrpcNativeHost.connect`, passing a reference to your binding's `initialize`. That call is what drives the native export above: loading the `.so` makes the symbol available, and `connect` invokes it to build *this* connection's native environment and run your `setup` lambda before handing back a connection ready to use:

```kotlin
import com.monkopedia.ksrpc.jni.JniSerialized
import com.monkopedia.ksrpc.jni.KsrpcNativeHost
import com.monkopedia.ksrpc.jni.NativeUtils
import com.monkopedia.ksrpc.toStub

// Load the Kotlin/Native shared library bundled on the classpath (once per process)
NativeUtils.loadLibraryFromJar("/libs/libmy_service.so")

// Open a connection: connect() invokes your binding's `initialize`, which builds
// this connection's own environment and runs the host's `setup` lambda against it.
val connection = KsrpcNativeHost.connect(scope, MyNativeService::initialize)

// Use the connection.
val service = connection.defaultChannel().toStub<MyService, JniSerialized>()
val result = service.someMethod("hello")
```

**Each `connect` call is independent** -- it drives a fresh `initialize` on the native side, so the connection gets its own native environment and its own service instance(s); call `connect` again and you get a separate connection with a separate host-side service, not a shared one. The native `setup` lambda has run against the connection before `connect` returns.

`KsrpcNativeHost.connect` defaults to a `JniSerialization` environment; pass your own `KsrpcEnvironment<JniSerialized>` to customize the JVM-side logger or error listener. The native side builds its own per-connection environment, tuned via the `configure` block passed to `ksrpcHostConnection`.

## Connection lifecycle

A `JniConnection` returned by `KsrpcNativeHost.connect` is a long-lived object intended to be reused across many calls.

- **Call `connect` once, reuse the connection.** Get the stub once via `connection.defaultChannel().toStub<MyService, JniSerialized>()` and make every call against that stub; do not reconnect per call. One warm connection per build (or per compilation, reused across all checker invocations) is the intended usage model.
- **Each `connect` call is independent.** A second `connect` drives a fresh `initialize` on the native side -- a new native environment and a new set of service instance(s) -- with no state shared with prior connections. Reconnecting therefore means starting fresh, not resuming.
- **Threading.** The JNI dispatcher is driven by JVM threads -- nothing on the native side spawns its own threads to attach. Call the stub's suspend functions from coroutines on your own scope; the `scope` argument to `connect` owns the connection's receive loop, so give it a scope whose lifetime matches the work the connection is for (e.g. one compilation, one build, one app session).
- **Teardown.** Call `connection.close()` once when the owning scope ends; cancelling or completing that scope also tears down the receive loop. `close()` is idempotent -- calling it more than once is a no-op on the second and subsequent calls (the underlying `PacketChannelBase.close()` short-circuits once already closed, the receive `Channel.close()` is itself idempotent, and the native counterpart short-circuits on its own already-closed flag).

```kotlin
val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
val connection = KsrpcNativeHost.connect(scope, MyNativeService::initialize)
val service = connection.defaultChannel().toStub<MyService, JniSerialized>()

try {
    // Many calls against the same warm connection.
    val a = service.someMethod("one")
    val b = service.someMethod("two")
} finally {
    connection.close()
    scope.cancel()
}
```

## Packaging the shared library

`NativeUtils.loadLibraryFromJar("/libs/libmy_service.so")` extracts the `.so` from the JVM classpath, so the linked library has to be placed there. The `sharedLib` from step 1 is produced under `build/bin/<target>/<buildType>Shared/lib<module>.so` (e.g. `build/bin/linuxX64/debugShared/libmy_service.so`); a Gradle copy task stages it into the JVM module's resources (under `libs/`) so `loadLibraryFromJar` can find it. Account for the per-OS extension (`.so` / `.dylib` / `.dll`) and the host's target.

The working end-to-end reference is the test harness:

- [`ksrpc-test/build.gradle.kts`](https://github.com/Monkopedia/ksrpc/blob/main/ksrpc-test/build.gradle.kts) -- the `sharedLib` export and the `copyLib` task that stages the built `.so` into JVM resources per host OS.
- [`ksrpc-test/src/nativeMain/kotlin/Test.kt`](https://github.com/Monkopedia/ksrpc/blob/main/ksrpc-test/src/nativeMain/kotlin/Test.kt) (the `initialize` export) and [`ksrpc-test/src/jvmTest/kotlin/JniTest.kt`](https://github.com/Monkopedia/ksrpc/blob/main/ksrpc-test/src/jvmTest/kotlin/JniTest.kt) (the `KsrpcNativeHost.connect` calls).

## Consuming `ksrpc-jni` from a Kotlin compiler plugin

When a Kotlin compiler plugin depends on `ksrpc-jni` (i.e. the dependency is wired onto a `kotlinCompilerPluginClasspath<Target>` configuration rather than a regular `implementation` configuration), the Kotlin Gradle Plugin defaults its compiler-plugin classpath configurations to `isTransitive = false`. That means a plain `add("kotlinCompilerPluginClasspathJvmMain", ...)` brings in `ksrpc-jni` itself but **not** `ksrpc-core`, `ksrpc-packets`, `kotlinx-coroutines-core`, `kotlinx-serialization-*`, etc., and the compiler-plugin classloader fails at runtime with `NoClassDefFoundError` on the first ksrpc symbol it touches.

This is KGP's compiler-plugin classpath behavior, not a ksrpc packaging issue. The fix is to flip the configuration to transitive (or list every transitive dependency by hand). For a multiplatform consumer that's loading the plugin on the JVM target:

```kotlin
configurations.named("kotlinCompilerPluginClasspathJvmMain") {
    isTransitive = true
}

dependencies {
    add("kotlinCompilerPluginClasspathJvmMain", "com.monkopedia.ksrpc:ksrpc-jni:$KSRPC_VERSION")
}
```

For a JVM-only consumer the configuration is `kotlinCompilerPluginClasspath`:

```kotlin
configurations.named("kotlinCompilerPluginClasspath") {
    isTransitive = true
}

dependencies {
    "kotlinCompilerPluginClasspath"("com.monkopedia.ksrpc:ksrpc-jni:$KSRPC_VERSION")
}
```

The exact configuration name follows KGP's `kotlinCompilerPluginClasspath<SourceSetName>` scheme -- pick the one matching the source set whose compilation should load the plugin (e.g. `kotlinCompilerPluginClasspathJvmMain`, `kotlinCompilerPluginClasspathJvmTest`).

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
