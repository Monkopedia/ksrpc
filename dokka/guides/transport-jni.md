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

## JVM side setup

On the JVM side, create a `JniConnection` with a `NativeKsrpcEnvironmentFactory` that initializes the native ksrpc environment:

```kotlin
import com.monkopedia.ksrpc.jni.JniConnection
import com.monkopedia.ksrpc.jni.NativeKsrpcEnvironmentFactory
import com.monkopedia.ksrpc.ksrpcEnvironment
import com.monkopedia.ksrpc.toStub

val env = ksrpcEnvironment { }
val connection = JniConnection(
    scope,
    env,
    nativeEnvironmentFactory
)

// Call a service hosted on the native side
val service = connection.defaultChannel().toStub<MyService>()
val result = service.someMethod("hello")

// Or host a service for the native side to call
connection.registerDefault(MyJvmServiceImpl())
```

The `NativeKsrpcEnvironmentFactory` is a functional interface that creates the native-side ksrpc environment pointer. Its implementation depends on how your native shared library is loaded and initialized.

## Native side

On the Kotlin/Native side, the `NativeConnection` is created automatically by the JNI bridge when `JniConnection.createConnection` is called. The native side uses the same `registerDefault` / `defaultChannel` pattern:

```kotlin
import com.monkopedia.ksrpc.jni.NativeConnection
import com.monkopedia.ksrpc.channels.registerDefault
import com.monkopedia.ksrpc.toStub

// Inside a JNI callback or initialization function
fun initializeService(connection: NativeConnection) {
    // Host a native service for the JVM to call
    connection.registerDefault(MyNativeServiceImpl())

    // Or call a service hosted on the JVM side
    val jvmService = connection.defaultChannel().toStub<JvmService>()
}
```

## Transport semantics

- Uses `JniSerialized` as the wire format -- a binary serialization format, not JSON
- Bidirectional: both JVM and Native sides can host and call services
- Full [`Connection`](https://monkopedia.github.io/ksrpc/ksrpc-core/com.monkopedia.ksrpc.channels/-connection/index.html) with sub-service support in both directions
- Zero network overhead -- data passes through JNI method calls in the same process
- Packet-based protocol using the same `PacketChannelBase` as other bidirectional transports
- Coroutine continuations are bridged across the JNI boundary, preserving suspend semantics

## See also

- [Bidirectional communication](bidirectional.md) -- two-way RPC patterns
- [Transports overview](transports.md) -- comparison of all transports
