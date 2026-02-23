# Optimization Log

This file tracks optimization attempts, measured impact, and final disposition.

Status values:
- `Not tested`: candidate idea identified but not yet implemented/measured.
- `Useful`: measurable improvement and kept.
- `Not useful`: no reliable gain or regression; reverted/not adopted.
- `Inconclusive`: mixed or noisy results; needs more profiling.

## 2026-02-22

### JNI serialization: replace boxed structure stacks with primitive `IntStack`
- Area: `ksrpc-jni` common encoder/decoder structure tracking.
- Status: `Useful`
- Change:
  - Replaced `mutableListOf<Int>()` stack usage with `IntStack` in:
    - `ksrpc-jni/src/commonMain/kotlin/com/monkopedia/ksrpc/jni/JniEncoder.kt`
    - `ksrpc-jni/src/commonMain/kotlin/com/monkopedia/ksrpc/jni/JniDecoder.kt`
  - Added:
    - `ksrpc-jni/src/commonMain/kotlin/com/monkopedia/ksrpc/jni/IntStack.kt`
- Benchmark evidence (JMH, payloadSize=256, `-wi 3 -i 6 -w 1s -r 2s -f 1`):
  - `decode`: `8,086,337 -> 9,778,284` ops/s (`+20.92%`)
  - `encode`: `5,687,677 -> 5,994,822` ops/s (`+5.40%`)
  - `encodeThenDecode`: `3,333,077 -> 4,001,206` ops/s (`+20.05%`)
- Decision: keep.

### JNI serialization: null structure placeholder + cached struct end + flattened slice decode
- Area: `ksrpc-jni` common encoder/decoder internals.
- Status: `Not useful`
- Change attempt:
  - In `JniEncoder.beginStructure`, wrote `null` placeholder instead of encoded `0`.
  - In `JniDecoder`, cached active structure end (`currentStructEnd`) to avoid frequent stack peeks.
  - Flattened nested `SlicedBasicList` in `decodeSerialized`.
- Benchmark evidence (isolated JMH rerun, payloadSize=256, same long settings):
  - `decode`: `9,778,284 -> 8,206,286` ops/s (`-16.08%`)
  - `encode`: `5,994,822 -> 5,215,331` ops/s (`-13.00%`)
  - `encodeThenDecode`: `4,001,206 -> 3,228,377` ops/s (`-19.31%`)
- Decision: reverted.

### Benchmarking note: avoid parallel benchmark jar rebuild + execution
- Status: `Useful` process lesson
- Observation:
  - Running `:ksrpc-bench:jvmBenchmarkJar` and `java -jar ...JMH.jar` in parallel caused invalid runs with `NoClassDefFoundError` (`ForkedMain`, `TreeMultimap`, `ResultFormatFactory`), likely due to jar rewrite during execution.
- Decision: build benchmark jar first, then run JMH sequentially.

### MultiChannel pending lookup: list scan to map lookup
- Area: `ksrpc-core` pending response matching.
- Status: `Useful`
- Change:
  - Replaced `pending` list with keyed map in:
    - `ksrpc-core/src/commonMain/kotlin/internal/MultiChannel.kt`
  - Switched response routing from linear scan/removal to direct keyed remove:
    - `send(id, response)` now uses `pending.remove(id)`.
  - Updated close path to complete all pending deferreds via map values.
- Benchmark evidence (JMH, `MultiChannelBenchmark.allocateSendReceive`, `-wi 3 -i 8 -w 1s -r 2s -f 1`):
  - `6,099,106.773 -> 7,078,354.536` ops/s (`+16.06%`)
- Validation:
  - `./gradlew allTests` passed (`BUILD SUCCESSFUL`).
- Decision: keep.

### Packet receive loop: inline response dispatch fast path
- Area: `ksrpc-packets` receive loop scheduling in `PacketChannelBase.executeReceive`.
- Status: `Not useful`
- Change attempt:
  - Inlined non-input/non-binary response delivery (`multiChannel.send`) to avoid one coroutine launch per response packet.
  - Kept coroutine launches for binary packets and input endpoint execution.
- Benchmark evidence (JMH, `SocketTransportBenchmark.socket(RoundTrip|BinaryRoundTrip)`, `payloadSize=256`, `-wi 3 -i 8 -w 1s -r 2s -f 1`):
  - `socketRoundTrip`: `32,646.470 -> 32,553.229` ops/s (`-0.29%`, essentially flat)
  - `socketBinaryRoundTrip`: `15,845.345 -> 9,812.347` ops/s (`-38.07%`, with high variance and worse central tendency)
  - Results saved:
    - `/tmp/socket-before.json`
    - `/tmp/socket-after-inline.json`
- Decision: reverted.

### Socket packet parsing: direct content-length scan instead of header map parse
- Area: `ksrpc-sockets` packet read path in `PacketUtils`/`ReadWritePacketChannel`.
- Status: `Not useful`
- Change attempt:
  - Added a direct `readContentLength()` scan and switched `readPacket()` to skip `readFields()` map creation.
  - Goal was lower per-packet allocation in socket framing read path.
- Benchmark evidence (JMH, `SocketTransportBenchmark.socket(RoundTrip|BinaryRoundTrip)`, `payloadSize=256`, `-wi 3 -i 8 -w 1s -r 2s -f 1`):
  - `socketRoundTrip`: `32,646.470 -> 24,179.461` ops/s (`-25.94%`)
  - `socketBinaryRoundTrip`: `15,845.345 -> 9,207.484` ops/s (`-41.89%`)
  - Results saved:
    - `/tmp/socket-before.json`
    - `/tmp/socket-after-headerparse-v2.json`
- Decision: reverted.

### Packet call path message IDs: allocate string IDs directly for packet callers
- Area: `ksrpc-core`/`ksrpc-packets` call-response message ID handling.
- Status: `Useful`
- Change:
  - Added `allocateReceiveString()` to `MultiChannel` to avoid duplicate `Int -> String` conversion for string-keyed callers.
  - Updated packet call path to use string-id allocation directly:
    - `ksrpc-packets/src/commonMain/kotlin/PacketChannelBase.kt`
  - Added coverage for new API:
    - `ksrpc-test/src/commonTest/kotlin/MultiChannelCoreTest.kt`
  - Added side-by-side benchmark method:
    - `ksrpc-bench/src/jvmMain/kotlin/com/monkopedia/ksrpc/bench/MultiChannelBenchmark.kt`
- Benchmark evidence:
  - `MultiChannelBenchmark` (same run, same settings) shows direct gain for string-id path:
    - `allocateSendReceive`: `5,422,733.712` ops/s
    - `allocateSendReceiveStringId`: `6,324,901.663` ops/s
    - Delta: `+16.63%` for string-id allocation path
  - End-to-end socket transport (same environment window) also moved up, though noisier:
    - `socketRoundTrip`: `22,706.670 -> 29,312.324` ops/s (`+29.09%`)
    - `socketBinaryRoundTrip`: `10,361.954 -> 13,538.003` ops/s (`+30.65%`)
  - Results saved:
    - `/tmp/multichannel-msgid-compare-v2.json`
    - `/tmp/socket-before-msgidstr.json`
    - `/tmp/socket-after-msgidstr.json`
- Validation:
  - `./gradlew allTests` passed (`BUILD SUCCESSFUL`).
- Decision: keep.

### JVM stream bridge benchmark attempt (`InputStream`/`OutputStream`)
- Area: `ksrpc-sockets` JVM stream bridge candidate (`InputOutputStreams.kt`).
- Status: `Inconclusive`
- Attempt:
  - Tried to add a dedicated benchmark for `Pair<InputStream, OutputStream>.asConnection`.
  - Benchmark setup consistently timed out (`InputOutputStreamTransportBenchmark timed out after 10000ms`).
- Observation:
  - Current stream bridge ties a long-lived writer coroutine to caller context, which prevents benchmark setup completion in this harness.
- Decision:
  - Reverted benchmark scaffolding; defer this optimization until scope/lifecycle semantics are addressed.

### JVM stream bridge: detach connection scope and remove per-chunk dispatcher hops
- Area: `ksrpc-sockets` JVM stream bridge (`InputOutputStreams.kt`).
- Status: `Useful`
- Change:
  - Reworked `Pair<InputStream, OutputStream>.asConnection` to use a dedicated `connectionScope`
    (`coroutineContext + SupervisorJob()`) and pass it explicitly into the packet connection.
  - Removed per-connection `newFixedThreadPoolContext` usage.
  - Removed per-chunk `withContext(Dispatchers.IO)` in `copyToAndFlush`; writes now run directly in
    the writer coroutine launched on `Dispatchers.IO`.
  - Added benchmark coverage:
    - `ksrpc-bench/src/jvmMain/kotlin/com/monkopedia/ksrpc/bench/InputOutputStreamTransportBenchmark.kt`
  - Added regression coverage for short-lived setup contexts:
    - `ksrpc-test/src/jvmTest/kotlin/SocketInputOutputStreamsJvmTest.kt`
- Benchmark evidence:
  - Before (previous state): benchmark setup timed out for all methods with
    `InputOutputStreamTransportBenchmark timed out after 10000ms` and no throughput results.
    - `/tmp/inputstream-transport-baseline-256.json`
  - After (`InputOutputStreamTransportBenchmark`, `payloadSize=32,256,2048`,
    `-wi 3 -i 8 -w 1s -r 2s -f 1`):
    - `inputOutputRoundTrip`: `20878.579` (32), `18832.399` (256), `15718.995` (2048) ops/s
    - `inputOutputComplexRoundTrip`: `16942.289` (32), `15776.211` (256), `10665.194` (2048) ops/s
    - `inputOutputBinaryRoundTrip`: `15527.994` (32), `13511.438` (256), `5652.652` (2048) ops/s
    - `/tmp/inputstream-transport-after-fix.json`
- Validation:
  - `./gradlew allTests` passed (`BUILD SUCCESSFUL`).
  - `./gradlew :ksrpc-sockets:ktlintJvmMainSourceSetCheck :ksrpc-test:ktlintJvmTestSourceSetCheck :ksrpc-bench:ktlintJvmMainSourceSetCheck`
    passed (`BUILD SUCCESSFUL`).
- Decision: keep.

### JNI connection bridge: cache packet serializer and int converter in `JniConnection`
- Area: `ksrpc-jni` JVM bridge hot path (`JniConnection.kt`).
- Status: `Not useful`
- Change attempt:
  - Cached `Packet.serializer(JniSerialized)` and `newTypeConverter<Any?>().int` as instance properties.
  - Replaced per-call lookup sites in `sendLocked`, `sendFromNative`, `close`, and `closeFromNative`.
- Benchmark evidence (`JniCommunicationBenchmark.jniRpcRoundTrip`, `payloadSize=32,256,2048`, `-wi 3 -i 8 -w 1s -r 2s -f 1`):
  - `payloadSize=32`: `93.406 -> 91.721` ops/s (`-1.80%`)
  - `payloadSize=256`: `93.471 -> 93.353` ops/s (`-0.13%`)
  - `payloadSize=2048`: `92.500 -> 92.081` ops/s (`-0.45%`)
  - Results saved:
    - `/tmp/jni-comm-before-cache.json`
    - `/tmp/jni-comm-after-cache.json`
- Decision: reverted.

### String serializer `isError`: single read of serialized payload
- Area: `ksrpc-core` common `StringSerializer.isError`.
- Status: `Inconclusive`
- Change attempt:
  - Read `CallData.readSerialized()` once in `isError` and reuse for both prefix checks.
- Benchmark evidence (`SocketTransportBenchmark.socketRoundTrip`, `payloadSize=32,256,2048`, `-wi 3 -i 8 -w 1s -r 2s -f 1`):
  - `payloadSize=32`: `35,444.405 -> 35,090.899` ops/s (`-1.00%`)
  - `payloadSize=256`: `32,582.050 -> 33,103.774` ops/s (`+1.60%`)
  - `payloadSize=2048`: highly unstable in both runs (large variance), no reliable conclusion.
  - Results saved:
    - `/tmp/socket-iserror-double-read.json` (baseline)
    - `/tmp/socket-iserror-single-read.json` (attempt)
- Observation:
  - Transport-level benchmark noise at larger payload dominated any micro-change from this branch.
- Decision:
  - Reverted in this transport-level attempt; later reintroduced and kept after a dedicated benchmark
    showed consistent gains (see `2026-02-23` entry below).

### BinaryChannel pending handling: iterative drain + parse-once out-of-order fast path
- Area: `ksrpc-packets` binary packet reassembly in `PacketChannelBase.BinaryChannel`.
- Status: `Useful`
- Change:
  - Parse `messageId` once per packet (`packetId`) and use a fast out-of-order path:
    - if not current packet, enqueue in `pending` and return.
  - Replaced recursive replay with iterative drain loop over `currentPacket`.
  - Added small map fast-path (`if (pending.isEmpty()) null else pending.remove(currentPacket)`).
  - Added regression coverage:
    - `ksrpc-test/src/commonTest/kotlin/PacketChannelBinaryOrderingTest.kt`
    - Verifies out-of-order binary chunks (`1`, `0`, terminator `2`) reassemble correctly.
- Benchmark evidence (`SocketTransportBenchmark.socketBinaryRoundTrip`, `payloadSize=32,256,2048`, `-wi 3 -i 8 -w 1s -r 2s -f 1`):
  - `payloadSize=32`: `21,466.487 -> 22,784.627` ops/s (`+6.14%`)
  - `payloadSize=256`: `11,012.866 -> 16,759.629` ops/s (`+52.18%`)
  - `payloadSize=2048`: `5,809.171 -> 5,831.617` ops/s (`+0.39%`)
  - Results saved:
    - `/tmp/socket-binary-before-pending-iter.json`
    - `/tmp/socket-binary-after-pending-iter.json`
- Validation:
  - `./gradlew allTests` passed (`BUILD SUCCESSFUL`).
- Decision: keep.

### Binary sender chunk reads: reusable buffer via `readAvailable`
- Area: `ksrpc-packets` binary send loop in `PacketChannelBase.sendPacket`.
- Status: `Not useful`
- Change attempt:
  - Replaced `readRemaining(maxSize).readBytes()` loop with:
    - reusable `ByteArray` buffer
    - `readAvailable(buffer, ...)`
    - per-chunk `copyOf(...)` before serialization.
- Benchmark evidence (`SocketTransportBenchmark.socketBinaryRoundTrip`, `payloadSize=32,256,2048`, `-wi 3 -i 8 -w 1s -r 2s -f 1`):
  - `payloadSize=32`: `20,653.205 -> 18,212.470` ops/s (`-11.82%`)
  - `payloadSize=256`: `16,268.445 -> 13,041.752` ops/s (`-19.83%`)
  - `payloadSize=2048`: `5,582.306 -> 2,636.383` ops/s (`-52.77%`)
  - Results saved:
    - `/tmp/socket-binary-before-send-readavail.json`
    - `/tmp/socket-binary-after-send-readavail.json`
- Decision: reverted.

### JsonRpc writer: cache serializers in `JsonRpcWriterBase`
- Area: `ksrpc-jsonrpc` request/response encode/decode hot path.
- Status: `Not useful`
- Change attempt:
  - Added cached serializers for `JsonRpcRequest`, `JsonRpcResponse`, and `RpcFailure`.
  - Replaced reified `encodeToJsonElement` / `decodeFromJsonElement` calls with explicit serializer overloads.
- Benchmark evidence (`JsonRpcWriterBenchmark.executeLoopbackRoundTrip`, `payloadSize=32,256,2048`, `-wi 3 -i 8 -w 1s -r 2s -f 1`):
  - `payloadSize=32`: `100,801.269 -> 83,100.628` ops/s (`-17.56%`)
  - `payloadSize=256`: `88,770.432 -> 90,274.516` ops/s (`+1.69%`)
  - `payloadSize=2048`: `86,083.598 -> 86,905.826` ops/s (`+0.96%`)
  - Results saved:
    - `/tmp/jsonrpc-writer-before-serializer-cache.json`
    - `/tmp/jsonrpc-writer-after-serializer-cache.json`
- Observation:
  - Mixed and noisy results, but a clear small-payload regression makes this change unsafe to keep.
- Decision: reverted.

### MultiChannel pending map: int-key storage + `send(Int, ...)` fast path
- Area: `ksrpc-core` pending response routing (`MultiChannel`).
- Status: `Not useful`
- Change attempt:
  - Switched pending map key type from `String` to `Int`.
  - Added `send(id: Int, response: T)` overload and `send(String, ...)` fallback parse.
  - Updated benchmark path to use the new int overload for `allocateSendReceive`.
- Benchmark evidence (`MultiChannelBenchmark`, `-wi 3 -i 8 -w 1s -r 2s -f 1`):
  - `allocateSendReceive`: `10,191,351.908 -> 9,869,011.604` ops/s (`-3.16%`)
  - `allocateSendReceiveStringId`: `6,950,871.569 -> 7,125,453.735` ops/s (`+2.51%`)
  - Results saved:
    - `/tmp/multichannel-intmap-before.json`
    - `/tmp/multichannel-intmap-after.json`
- Observation:
  - Mixed result with a regression in the core int-id benchmark path; not a clear win.
- Decision: reverted.

### Binary sender chunk extraction: swap `readBytes()` to `readByteArray()`
- Area: `ksrpc-packets` binary send loop in `PacketChannelBase.sendPacket`.
- Status: `Not useful`
- Change attempt:
  - Replaced `readRemaining(maxSize).readBytes()` with `readByteArray()`.
  - Added `kotlinx.io.readByteArray` import (the API is available in current source set).
- Benchmark evidence (`SocketTransportBenchmark`, `payloadSize=256`, `-wi 3 -i 8 -w 1s -r 2s -f 1`):
  - Baseline:
    - `/tmp/socket-before-readbytearray-r2.json`
    - `socketBinaryRoundTrip`: `13,112.349` ops/s
    - `socketRoundTrip`: `30,896.422` ops/s
  - Attempt:
    - `/tmp/socket-after-readbytearray-r2.json`
    - `socketBinaryRoundTrip`: `13,969.085` ops/s (`+6.53%`)
    - `socketRoundTrip`: `26,856.813` ops/s (`-13.07%`)
  - Focused binary-only rerun (same settings):
    - Baseline: `/tmp/socket-before-readbytearray-binary-r3.json` -> `14,584.757` ops/s
    - Attempt: `/tmp/socket-after-readbytearray-binary-r3.json` -> `13,872.844` ops/s (`-4.88%`)
- Observation:
  - Results were noisy and directionally inconsistent; focused binary rerun regressed.
- Decision:
  - Reverted.

### MultiChannel locking: replace `Mutex` with atomicfu `SynchronizedObject`
- Area: `ksrpc-core` pending-response synchronization in `MultiChannel`.
- Status: `Not useful`
- Change attempt:
  - Replaced suspend `Mutex.withLock` critical sections with `SynchronizedObject` + `synchronized`.
  - Kept API and behavior unchanged (`send`, `allocateReceive`, `allocateReceiveString`, `close`).
- Benchmark evidence:
  - `MultiChannelBenchmark` (`-wi 3 -i 8 -w 1s -r 2s -f 1`):
    - `allocateSendReceive`: `10,191,351.908 -> 8,227,590.185` ops/s (`-19.27%`)
    - `allocateSendReceiveStringId`: `6,950,871.569 -> 9,176,377.135` ops/s (`+32.02%`)
    - Results:
      - `/tmp/multichannel-intmap-before.json` (baseline)
      - `/tmp/multichannel-sync-lock-after.json` (attempt)
  - End-to-end socket transport check (`payloadSize=256`, same settings):
    - `socketBinaryRoundTrip`: `15,745.216 -> 13,817.144` ops/s (`-12.25%`)
    - `socketRoundTrip`: `33,028.545 -> 32,730.424` ops/s (`-0.90%`)
    - Results:
      - `/tmp/socket-transport-sync-lock-before-256.json`
      - `/tmp/socket-transport-sync-lock-after-256.json`
- Observation:
  - Despite one microbenchmark improving, transport-level checks regressed.
- Decision: reverted.

### PacketChannel serializer caching: reuse `String`/`ByteArray`/`Unit` serializers
- Area: `ksrpc-packets` hot path serializer lookups in `PacketChannelBase`.
- Status: `Not useful`
- Change attempt:
  - Added top-level cached serializer vals and replaced repeated calls to:
    - `String.serializer()`
    - `ByteArraySerializer()`
    - `Unit.serializer()`
  - Updated binary send/decode and control-message call sites to reuse cached serializers.
- Benchmark evidence:
  - Full run (`SocketTransportBenchmark.socket(RoundTrip|BinaryRoundTrip)`, `payloadSize=32,256,2048`):
    - Mixed results with wins in some cases and regressions in others.
    - Results:
      - `/tmp/socket-transport-before-packet-serializer-cache.json`
      - `/tmp/socket-transport-after-packet-serializer-cache.json`
  - Focused confirmation (`payloadSize=256`, same settings):
    - `socketBinaryRoundTrip`: `16,405.831 -> 18,577.316` ops/s (`+13.24%`)
    - `socketRoundTrip`: `35,521.874 -> 33,822.955` ops/s (`-4.78%`)
    - Results:
      - `/tmp/socket-transport-before-packet-serializer-cache-256-r2.json`
      - `/tmp/socket-transport-after-packet-serializer-cache-256-r2.json`
- Observation:
  - Binary path improved, but non-binary round-trip regressed in confirmation runs.
- Decision: reverted.

### PacketChannel binary serializer caching: `ByteArraySerializer` only
- Area: `ksrpc-packets` binary packet encode/decode path in `PacketChannelBase`.
- Status: `Not useful`
- Change attempt:
  - Added a cached `ByteArraySerializer()` value and used it only for:
    - binary chunk send serialization
    - binary terminator packet serialization
    - binary chunk decode in `BinaryChannel.handlePacket`
  - Left `String` and `Unit` serializer call sites unchanged.
- Benchmark evidence:
  - Full run (`SocketTransportBenchmark.socket(RoundTrip|BinaryRoundTrip)`, `payloadSize=32,256,2048`,
    `-wi 3 -i 8 -w 1s -r 2s -f 1`) was noisy and mixed:
    - `/tmp/socket-binary-before-bytearray-only-cache.json`
    - `/tmp/socket-binary-after-bytearray-only-cache.json`
  - Focused A/B confirmation (`payloadSize=256`, same settings) showed negligible movement:
    - `socketBinaryRoundTrip`: `19191.863 -> 19244.719` ops/s (`+0.28%`)
    - `socketRoundTrip`: `36115.811 -> 36182.668` ops/s (`+0.19%`)
    - `/tmp/socket-before-bytearray-only-cache-256-r3.json`
    - `/tmp/socket-after-bytearray-only-cache-256-r3.json`
- Observation:
  - Effect size is within noise; no meaningful transport-level gain.
- Decision: reverted.

### Ktor websocket packet conversion: direct `Json` + frame codec
- Area: `ksrpc-ktor/websocket/shared` `WebsocketPacketChannel`.
- Status: `Not useful`
- Change attempt:
  - Replaced `KotlinxWebsocketSerializationConverter` + `typeInfo` path with direct encode/decode:
    - send: `Packet<String>` -> `Json.encodeToString(...)` -> `Frame.Text`
    - receive: `Frame.Text`/`Frame.Binary` payload -> `Json.decodeFromString(...)`
- Benchmark evidence:
  - Full run (`WebsocketTransportBenchmark.websocket(RoundTrip|BinaryRoundTrip)`,
    `payloadSize=32,256,2048`, `-wi 3 -i 8 -w 1s -r 2s -f 1`):
    - `websocketBinaryRoundTrip`: `-1.46%` (32), `-0.28%` (256), `+6.81%` (2048)
    - `websocketRoundTrip`: `-13.78%` (32, noisy outlier run), `+3.98%` (256), `+8.12%` (2048)
    - Results:
      - `/tmp/websocket-codec-before.json`
      - `/tmp/websocket-codec-after.json`
  - Focused confirmation (`payloadSize=32`, same settings):
    - `websocketBinaryRoundTrip`: `5882.202 -> 5813.033` ops/s (`-1.18%`)
    - `websocketRoundTrip`: `11070.559 -> 11046.626` ops/s (`-0.22%`)
    - Result:
      - `/tmp/websocket-codec-after-32-r2.json`
- Observation:
  - Mixed full-run results and near-flat focused confirmation; no consistent measurable gain.
- Decision: reverted.

### Logger lazy-message API in packet hot path
- Area: `ksrpc-core` `Logger` API and `ksrpc-packets` `PacketChannelBase` hot-path logging call sites.
- Status: `Not useful`
- Change attempt:
  - Added `Logger` helpers with `isEnabled(level, tag)` and lazy message overloads (`message: () -> String`)
    for `debug/info/warn/error`.
  - Converted interpolated logging calls in `PacketChannelBase` to lazy form.
- Benchmark evidence:
  - Full run (`SocketTransportBenchmark.socket(RoundTrip|BinaryRoundTrip)`,
    `payloadSize=32,256,2048`, `-wi 3 -i 8 -w 1s -r 2s -f 1`):
    - `socketBinaryRoundTrip`: `+0.48%` (32), `-0.63%` (256), `-1.09%` (2048)
    - `socketRoundTrip`: noisy `-12.98%` (32, outlier-affected), `+2.74%` (256), `+1.21%` (2048)
    - Results:
      - `/tmp/socket-lazylog-before.json`
      - `/tmp/socket-lazylog-after.json`
  - Focused confirmation (`payloadSize=32`, same settings):
    - `socketBinaryRoundTrip`: `25384.207 -> 25243.072` ops/s (`-0.56%`)
    - `socketRoundTrip`: `37926.736 -> 36970.970` ops/s (`-2.52%`, high variance)
    - Result:
      - `/tmp/socket-lazylog-after-32-r2.json`
- Observation:
  - No consistent transport-level uplift; movements were small/mixed and often within noisy ranges.
- Decision: reverted.

### Packet binary path serializer fast-paths: `createSerialized` / `decodeSerialized`
- Area: `ksrpc-core` serializer API + `ksrpc-packets` binary packet hot path.
- Status: `Not useful`
- Change attempt:
  - Added default serializer fast-path helpers on `CallDataSerializer<T>`:
    - `createSerialized(serializer, input): T`
    - `decodeSerialized(serializer, data: T): I`
  - Overrode fast-path methods in:
    - `ksrpc-core` string serializer
    - `ksrpc-jni` serializer
  - Updated binary packet path in `PacketChannelBase` to use fast-path APIs and skip
    `CallData.create(...).readSerialized()` / `decodeCallData(..., CallData.create(...))` wrappers.
  - Added serializer parity tests in `CallDataSerializerErrorHandlingTest`.
- Benchmark evidence:
  - Full run (`SocketTransportBenchmark.socket(RoundTrip|BinaryRoundTrip)`,
    `payloadSize=32,256,2048`, `-wi 3 -i 8 -w 1s -r 2s -f 1`):
    - Baseline: `/tmp/socket-before-serializer-fastpath.json`
    - Attempt: `/tmp/socket-after-serializer-fastpath.json`
    - Observed mixed/noisy movement (including regressions on larger payload cases).
  - Focused confirmation (`payloadSize=256`, same settings):
    - Attempt rerun: `/tmp/socket-after-serializer-fastpath-256-r2.json`
    - Baseline control rerun: `/tmp/socket-before-serializer-fastpath-256-r2.json`
    - Binary and non-binary results remained highly variable across reruns, with no stable,
      repeatable gain attributable to the change.
- Observation:
  - Effect size was not robust under reruns; transport noise dominated the measured deltas.
- Decision: reverted.

### JNI call-data envelope: flatten wrapper to fixed header + payload slice
- Area: `ksrpc-jni` call-data envelope in `JniSerialization`.
- Status: `Useful`
- Change:
  - Replaced nested wrapper serialization (`Wrapper(isError, content: JniSerialized, ...)`) with a flat
    envelope layout:
    - `[isError, isEndpointMissing, payloadSize, payload...]`
  - Encode path now serializes payload once, then prepends a fixed header.
  - Decode path reads header directly and slices payload without wrapper deserialization.
  - Added compatibility fallback on decode for legacy wrapper-encoded payloads.
  - Files changed:
    - `ksrpc-jni/src/commonMain/kotlin/com/monkopedia/ksrpc/jni/JniSerialization.kt`
- Benchmark evidence:
  - `JniCommunicationBenchmark.jniRpcRoundTrip` (`payloadSize=32,256,2048`,
    `-wi 3 -i 8 -w 1s -r 2s -f 1`):
    - Before: `/tmp/jni-comm-before-envelope-flatten.json`
    - After: `/tmp/jni-comm-after-envelope-flatten.json`
    - `payloadSize=32`: `89.270 -> 90.552` ops/s (`+1.44%`)
    - `payloadSize=256`: `88.912 -> 90.214` ops/s (`+1.46%`)
    - `payloadSize=2048`: `88.379 -> 91.981` ops/s (`+4.08%`, noisier)
  - Focused confirmation (`payloadSize=256`, same settings):
    - `/tmp/jni-comm-after-envelope-flatten-256-r2.json`
    - `91.470` ops/s (still above baseline `88.912`).
- Validation:
  - `./gradlew allTests` passed (`BUILD SUCCESSFUL`).
- Decision: keep.

## 2026-02-23

### JsonRpc header receive path: direct `Content-Length` scan
- Area: `ksrpc-jsonrpc` header parse path in `JsonRpcTransformer`.
- Status: `Useful`
- Change:
  - Replaced `readFields()` map parsing in `JsonRpcHeader.receive()` with direct line-by-line
    `Content-Length` extraction (`readContentLength()`).
  - This removes header map allocation and extra key lookups in the receive hot path.
  - Added benchmark coverage:
    - `ksrpc-bench/src/jvmMain/kotlin/com/monkopedia/ksrpc/bench/JsonRpcHeaderBenchmark.kt`
- Benchmark evidence (`JsonRpcHeaderBenchmark.sendThenReceive`,
  `payloadSize=32,256,2048`, `-wi 3 -i 8 -w 1s -r 2s -f 1`):
  - Baseline:
    - `/tmp/jsonrpc-header-before-jvmcodec.json`
    - `32`: `886,304.905` ops/s
    - `256`: `747,225.631` ops/s (noisy run)
    - `2048`: `447,541.790` ops/s
  - Attempt:
    - `/tmp/jsonrpc-header-after-contentlength-scan.json`
    - `32`: `986,110.337` ops/s (`+11.26%`)
    - `256`: `843,864.255` ops/s (`+12.94%` vs baseline run)
    - `2048`: `416,752.777` ops/s (full-run regression/noise)
  - Focused confirmation (`payloadSize=2048`, same settings):
    - `/tmp/jsonrpc-header-after-contentlength-scan-2048-r2.json`
    - `448,264.202` ops/s (effectively flat/slightly above baseline `447,541.790`)
- Validation:
  - `./gradlew allTests` passed (`BUILD SUCCESSFUL`).
  - `./gradlew :ksrpc-jsonrpc:ktlintCommonMainSourceSetCheck :ksrpc-bench:ktlintJvmMainSourceSetCheck`
    passed (`BUILD SUCCESSFUL`).
- Decision: keep.

### JsonRpc header receive path: reusable buffer for payload decode
- Area: `ksrpc-jsonrpc` `JsonRpcHeader.receive()`.
- Status: `Not useful`
- Change attempt:
  - Reused a mutable `ByteArray` receive buffer and decoded from a `[0, length)` slice to avoid
    per-message byte array allocation.
- Benchmark evidence (`JsonRpcHeaderBenchmark.sendThenReceive`,
  `payloadSize=32,256,2048`, `-wi 3 -i 8 -w 1s -r 2s -f 1`):
  - Baseline (post-`Content-Length` scan version):
    - `/tmp/jsonrpc-header-after-contentlength-scan.json`
    - `32`: `986,110.337` ops/s
    - `256`: `843,864.255` ops/s
    - `2048`: `416,752.777` ops/s
  - Attempt:
    - `/tmp/jsonrpc-header-after-buffer-reuse.json`
    - `32`: `971,523.163` ops/s (`-1.48%`)
    - `256`: `796,424.122` ops/s (`-5.62%`)
    - `2048`: `460,151.443` ops/s (mixed gain)
  - Focused confirmation (`payloadSize=256`, same settings):
    - `/tmp/jsonrpc-header-after-buffer-reuse-256-r2.json`
    - `810,162.780` ops/s (still below baseline `843,864.255`).
- Decision: reverted.

### JsonRpc dispatch parse: manual `JsonObject` extraction for request/response
- Area: `ksrpc-jsonrpc` receive loop dispatch in `JsonRpcWriterBase`.
- Status: `Inconclusive`
- Change:
  - Replaced `json.decodeFromJsonElement<JsonRpcRequest/JsonRpcResponse>(...)` in the hot receive
    loop with strict/manual `JsonObject` field extraction:
    - request detection/parsing via `parseRequestOrNull(...)`
    - response parsing via `parseResponse(...)`
    - error parsing via `parseError(...)`
  - This avoids serializer-driven object decode in the dispatch branch while preserving error
    signaling for malformed request/response payloads.
  - File changed:
    - `ksrpc-jsonrpc/src/commonMain/kotlin/JsonRpcWriterBase.kt`
- Benchmark evidence (`JsonRpcWriterBenchmark.executeLoopbackRoundTrip`,
  `payloadSize=32,256,2048`, `-wi 3 -i 8 -w 1s -r 2s -f 1`):
  - Baseline:
    - `/tmp/jsonrpc-writer-before-manual-parse.json`
    - `32`: `106,551.765` ops/s
    - `256`: `92,432.734` ops/s (noisy run)
    - `2048`: `102,948.715` ops/s
  - Attempt:
    - `/tmp/jsonrpc-writer-after-manual-parse.json`
    - `32`: `105,648.605` ops/s (`-0.85%`)
    - `256`: `107,675.075` ops/s (`+16.49%` vs baseline run)
    - `2048`: `103,028.389` ops/s (`+0.08%`)
  - Controlled focused confirmation (`payloadSize=256`, same settings):
    - Baseline control: `/tmp/jsonrpc-writer-before-manual-parse-256-r2.json`
      - `101,089.146` ops/s
    - Attempt: `/tmp/jsonrpc-writer-after-manual-parse-256-r2.json`
      - `107,379.714` ops/s
    - Delta: `+6.22%`
  - Additional attempt reruns (`payloadSize=256`, same settings) were unstable:
    - `/tmp/jsonrpc-writer-after-manual-parse-256-r3.json`: `100,995.357` ops/s (near baseline)
    - `/tmp/jsonrpc-writer-after-manual-parse-256-r4.json`: `87,368.339` ops/s (regressed)
- Observation:
  - Measured throughput moved significantly across reruns, including clear regressions, so the
    change does not provide a reliable gain in this environment.
- Decision: reverted.

### BinaryChannel in-order fast path: compare `messageId` string before `toInt()`
- Area: `ksrpc-packets` binary reassembly hot path in `PacketChannelBase.BinaryChannel`.
- Status: `Not useful`
- Change attempt:
  - Added `currentPacketString` and compared incoming `packet.messageId` string directly for the
    common in-order path.
  - Deferred `messageId.toInt()` parsing to out-of-order packets only.
- Benchmark evidence (`SocketTransportBenchmark.socketBinaryRoundTrip` and `socketRoundTrip`,
  `payloadSize=32,256,2048`, `-wi 3 -i 8 -w 1s -r 2s -f 1`):
  - Baseline:
    - `/tmp/socket-binary-inorder-before.json`
    - Binary: `32` `23,326.958`, `256` `17,723.187`, `2048` `5,709.927` ops/s
    - Non-binary: `32` `26,458.449`, `256` `31,844.971`, `2048` `21,405.412` ops/s
  - Attempt:
    - `/tmp/socket-binary-inorder-after.json`
    - Binary: `32` `22,223.609` (`-4.73%`), `256` `14,446.282` (`-18.49%`, noisy),
      `2048` `5,949.821` (`+4.20%`)
    - Non-binary: `32` `34,079.817`, `256` `31,248.620` (`-1.87%`), `2048` `21,931.777`
  - Focused confirmation (`payloadSize=256`, same settings):
    - `/tmp/socket-binary-inorder-after-256-r2.json`
    - Binary: `16,802.441` ops/s (still below baseline `17,723.187`, about `-5.20%`)
    - Non-binary: `33,293.506` ops/s (mixed/noisy)
- Observation:
  - Targeted binary path did not improve reliably and regressed in focused confirmation.
- Decision: reverted.

### MultiChannel lock scope: complete deferreds outside mutex
- Area: `ksrpc-core` pending-response synchronization in `MultiChannel`.
- Status: `Not useful`
- Change attempt:
  - Reduced mutex scope in `send` and `close` by removing deferreds under lock and calling
    `complete(...)` / `completeExceptionally(...)` after releasing the mutex.
- Benchmark evidence (`MultiChannelBenchmark`, `-wi 3 -i 8 -w 1s -r 2s -f 1`):
  - Baseline:
    - `/tmp/multichannel-before-complete-outside-lock.json`
    - `allocateSendReceive`: `7,661,388.592` ops/s
    - `allocateSendReceiveStringId`: `8,786,507.419` ops/s
  - Attempt:
    - `/tmp/multichannel-after-complete-outside-lock.json`
    - `allocateSendReceive`: `7,533,060.101` ops/s (`-1.68%`)
    - `allocateSendReceiveStringId`: `8,609,638.628` ops/s (`-2.01%`)
- Observation:
  - Both benchmark variants regressed in the same run configuration.
- Decision: reverted.

### JsonRpc header send path: write UTF-8 string directly + computed UTF-8 length
- Area: `ksrpc-jsonrpc` `JsonRpcHeader.send()`.
- Status: `Not useful`
- Change attempt:
  - Replaced `encodeToString(...).encodeToByteArray()` + `writeFully(byte[])` with:
    - direct `writeStringUtf8(content)`
    - computed UTF-8 byte length via a `utf8Length()` helper for `Content-Length`.
  - Goal: avoid payload `ByteArray` allocation and reduce conversion overhead in the send path.
- Benchmark evidence (`JsonRpcHeaderBenchmark.sendThenReceive`,
  `payloadSize=32,256,2048`, `-wi 3 -i 8 -w 1s -r 2s -f 1`):
  - Baseline:
    - `/tmp/jsonrpc-header-before-bytecodec.json`
    - `32`: `870,590.721` ops/s
    - `256`: `739,881.528` ops/s
    - `2048`: `341,366.715` ops/s
  - Attempt:
    - `/tmp/jsonrpc-header-after-utf8len-writeString.json`
    - `32`: `680,143.293` ops/s (`-21.88%`, noisy but materially lower)
    - `256`: `733,327.921` ops/s (`-0.89%`)
    - `2048`: `324,615.384` ops/s (`-4.91%`)
- Observation:
  - Regressed or flat across all payload sizes in this run.
- Decision: reverted.

### Native POSIX write path: remove per-chunk sync/copy and handle partial writes
- Area: `ksrpc-sockets` native write bridge in `PosixFileReadChannel.kt`.
- Status: `Useful`
- Change:
  - Removed per-chunk `fsync(fd)` and `fflush(null)` calls from the stream write loop.
  - Removed per-chunk `sliceArray(...).toCValues()` allocation.
  - Switched to pinned buffer pointer writes and added proper partial-write handling:
    - loop until full chunk is written
    - retry on `EINTR`
    - fail fast on no-progress writes
- Why this is kept:
  - Correctness: previous code could drop bytes on partial `write(...)` results.
  - Performance: removed expensive durability/sync calls and copy allocations from hot path.
- Native benchmark support:
  - Added `linuxX64` native benchmark executable target in:
    - `ksrpc-bench/build.gradle.kts`
  - Added native POSIX pipe microbenchmark harness in:
    - `ksrpc-bench/src/linuxX64Main/kotlin/com/monkopedia/ksrpc/bench/PosixFileChannelMicrobench.kt`
- Benchmark evidence (native executable, `--warmup=500 --iterations=4000 --payloads=256,4096,16384`,
  3 baseline runs from `35c1bea^` vs 3 optimized runs from `35c1bea`):
  - Median throughput deltas:
    - `256`: `60,931.364 -> 66,670.841` ops/s (`+9.42%`)
    - `4096`: `37,474.390 -> 62,329.155` ops/s (`+66.32%`)
    - `16384`: `20,426.728 -> 49,228.390` ops/s (`+141.00%`)
  - Median latency deltas:
    - `256`: `16,411.909 -> 14,999.061` ns/op (`-8.61%`)
    - `4096`: `26,684.891 -> 16,043.856` ns/op (`-39.88%`)
    - `16384`: `48,955.466 -> 20,313.482` ns/op (`-58.51%`)
- Validation:
  - `./gradlew allTests` passed (`BUILD SUCCESSFUL`).
  - `./gradlew :ksrpc-sockets:ktlintNativeMainSourceSetCheck` passed (`BUILD SUCCESSFUL`).
- Decision: keep.

### String serializer `isError`: single read of serialized payload (dedicated benchmark)
- Area: `ksrpc-core` common `StringSerializer.isError`.
- Status: `Useful`
- Change:
  - Read `CallData.readSerialized()` once in `isError` and reuse it for both error-prefix checks.
  - Added dedicated benchmark coverage:
    - `ksrpc-bench/src/commonMain/kotlin/com/monkopedia/ksrpc/bench/StringSerializerErrorBenchmark.kt`
- Benchmark evidence (`StringSerializerErrorBenchmark.(isErrorNormal|isErrorError|isErrorEndpointMissing)`,
  `payloadSize=32,256,2048`, `-wi 3 -i 8 -w 1s -r 2s -f 1`):
  - Baseline:
    - `/tmp/string-iserror-before-single-read.json`
  - Attempt:
    - `/tmp/string-iserror-after-single-read.json`
  - Deltas:
    - `isErrorEndpointMissing`: `+6.16%` (32), `+4.40%` (256), `+14.20%` (2048)
    - `isErrorError`: `+3.44%` (32), `+7.45%` (256), `+3.25%` (2048)
    - `isErrorNormal`: `+9.41%` (32), `+2.24%` (256), `+3.17%` (2048)
- Validation:
  - `./gradlew :ksrpc-core:ktlintCommonMainSourceSetCheck :ksrpc-bench:ktlintCommonMainSourceSetCheck`
    passed (`BUILD SUCCESSFUL`).
  - `./gradlew allTests` passed (`BUILD SUCCESSFUL`).
- Decision: keep.

## 2026-02-22 (Backlog)

### Prioritized optimization candidates (not tested)

#### P1

### Native POSIX write path: avoid fsync/fflush and sliceArray per chunk
- Status: `Useful`
- Area:
  - `ksrpc-sockets/src/nativeMain/kotlin/PosixFileReadChannel.kt`
- Why:
  - `fsync(fd)` and `fflush(null)` on every chunk are very expensive.
  - `sliceArray(...).toCValues()` allocates on every write.
- Try:
  - Implemented in `2026-02-23` entry above, including partial-write handling.

### Binary transport path: reduce encode/decode hops for chunked binary
- Status: `Inconclusive`
- Area:
  - `ksrpc-packets/src/commonMain/kotlin/PacketChannelBase.kt`
- Why:
  - Binary chunks are wrapped in packet serialization, often via string serialization.
  - Multiple conversions per chunk can dominate throughput.
- Try:
  - Prior attempt (`createSerialized`/`decodeSerialized` fast-path wrappers) did not show a stable
    transport-level gain; see entry above.
  - Transport-specific direct binary frame path (sockets/websocket/jni) where possible.
  - Keep packet metadata separate from binary payload bytes.

#### P2

### Packet message ID type: use numeric IDs in hot path
- Status: `Not useful`
- Area:
  - `ksrpc-packets/src/commonMain/kotlin/Packet.kt`
  - `ksrpc-packets/src/commonMain/kotlin/PacketChannelBase.kt`
  - `ksrpc-core/src/commonMain/kotlin/internal/MultiChannel.kt`
- Why:
  - Frequent `Int -> String` and `String -> Int` conversions (`toString()`, `toInt()`).
- Try:
  - Introduce numeric message ID representation internally and convert only at boundaries.
  - Partial attempt via int-key pending map/fast-path (`2026-02-22` entry) regressed core benchmark
    path and was reverted.

### JsonRpc header transformer: avoid extra string/byte conversions
- Status: `Not useful`
- Area:
  - `ksrpc-jsonrpc/src/commonMain/kotlin/JsonRpcTransformer.kt`
- Why:
  - `encodeToString` -> `encodeToByteArray`, then `ByteArray` -> `decodeToString` -> `decodeFromString`.
- Try:
  - Explore direct byte-oriented JSON encode/decode path or pooling for reusable byte buffers.
  - Prior send-path conversion attempt (`utf8Length` + direct `writeStringUtf8`) regressed and was
    reverted (`2026-02-23` entry).

### JsonRpc request/response dispatch parse path: reduce intermediate JSON allocations
- Status: `Inconclusive`
- Area:
  - `ksrpc-jsonrpc/src/commonMain/kotlin/JsonRpcWriterBase.kt`
- Why:
  - Decode to `JsonObject`/`JsonElement` first, then decode again to typed request/response.
- Try:
  - Manual extraction attempt was benchmarked but remained unstable across reruns;
    see `2026-02-23` entry above.

### JNI connection bridge: cache serializers/converters used per call
- Status: `Not useful`
- Area:
  - `ksrpc-jni/src/jvmMain/kotlin/com/monkopedia/ksrpc/jni/JniConnection.kt`
- Why:
  - Repeated `Packet.serializer(JniSerialized)` and converter construction in hot methods.
- Try:
  - Cache packet serializer and int converter on instance initialization.
  - Attempted and regressed (`2026-02-22` entry); reverted.

### JNI call-data envelope: remove nested wrapper serialization when possible
- Status: `Useful`
- Area:
  - `ksrpc-jni/src/commonMain/kotlin/com/monkopedia/ksrpc/jni/JniSerialization.kt`
- Why:
  - Current flow wraps serialized payload in another serialized wrapper object.
- Try:
  - Evaluate flatter envelope encoding for error bit + endpoint-missing flag + payload.

#### P3

### Logging overhead in hot paths: add lazy message formatting
- Status: `Not useful`
- Area:
  - Frequent call sites in packet/channel code
  - `ksrpc-core/src/commonMain/kotlin/Logger.kt`
- Why:
  - String interpolation occurs even when default no-op logger drops logs.
- Try:
  - Add lambda-based logging API or explicit level checks before building expensive messages.
  - Attempted and benchmarked (`2026-02-22` entry) with no reliable gain; reverted.

### String serializer error checks: reduce repeated serialized reads
- Status: `Useful`
- Area:
  - `ksrpc-core/src/commonMain/kotlin/KsrpcEnvironment.kt` (`StringSerializer.isError`)
- Why:
  - `readSerialized()` called multiple times on same `CallData`.
- Try:
  - Read once and reuse local value in predicates.
  - Implemented and retained (`2026-02-23` dedicated benchmark entry above).

### Service worker packet channels (JS/Wasm): reduce queue and serialization overhead
- Status: `Not tested`
- Area:
  - `ksrpc-service-worker/src/jsMain/.../ServiceWorkerConnectionsJs.kt`
  - `ksrpc-service-worker/src/wasmJsMain/.../ServiceWorkerConnectionsWasm.kt`
- Why:
  - `Channel.UNLIMITED` can grow without backpressure.
  - Packet payloads are string-serialized for each postMessage.
- Try:
  - Backpressure-aware channel sizing and structured-clone-friendly payload formats.

### Ktor websocket packet conversion path: evaluate lower-overhead codec
- Status: `Not useful`
- Area:
  - `ksrpc-ktor/websocket/shared/src/commonMain/kotlin/WebsocketPacketChannel.kt`
- Why:
  - Generic converter path may add overhead per frame.
- Try:
  - Compare against direct serializer-based frame read/write with reusable buffers.
  - Attempted direct frame codec and reverted due mixed/flat results (`2026-02-22` entry).
