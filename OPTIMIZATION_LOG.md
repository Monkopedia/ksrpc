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
  - Reverted code change; keep as a possible micro-optimization if a tighter benchmark harness is added.

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
- Status: `Inconclusive`
- Change attempt:
  - Replaced `readRemaining(maxSize).readBytes()` with `readByteArray()`.
- Outcome:
  - Compilation failed: `Unresolved reference 'readByteArray'` for current source-set API.
- Decision:
  - Reverted immediately; no benchmark run performed.

## 2026-02-22 (Backlog)

### Prioritized optimization candidates (not tested)

#### P1

### JVM stream bridge: remove per-chunk context switching and aggressive flush
- Status: `Not tested`
- Area:
  - `ksrpc-sockets/src/jvmMain/kotlin/InputOutputStreams.kt`
- Why:
  - `copyToAndFlush()` does `withContext(Dispatchers.IO)` and `flush()` for each chunk.
  - `newFixedThreadPoolContext` is created per connection.
- Try:
  - Write/flush policy tuning (flush on boundaries / close, not every chunk).
  - Replace per-connection fixed pool with shared dispatcher or dedicated single writer context.

### Native POSIX write path: avoid fsync/fflush and sliceArray per chunk
- Status: `Not tested`
- Area:
  - `ksrpc-sockets/src/nativeMain/kotlin/PosixFileReadChannel.kt`
- Why:
  - `fsync(fd)` and `fflush(null)` on every chunk are very expensive.
  - `sliceArray(...).toCValues()` allocates on every write.
- Try:
  - Remove per-chunk durability calls for stream transport.
  - Write directly from buffer/pointer without array copies.

### Binary transport path: reduce encode/decode hops for chunked binary
- Status: `Not tested`
- Area:
  - `ksrpc-packets/src/commonMain/kotlin/PacketChannelBase.kt`
- Why:
  - Binary chunks are wrapped in packet serialization, often via string serialization.
  - Multiple conversions per chunk can dominate throughput.
- Try:
  - Transport-specific direct binary frame path (sockets/websocket/jni) where possible.
  - Keep packet metadata separate from binary payload bytes.

#### P2

### Packet message ID type: use numeric IDs in hot path
- Status: `Not tested`
- Area:
  - `ksrpc-packets/src/commonMain/kotlin/Packet.kt`
  - `ksrpc-packets/src/commonMain/kotlin/PacketChannelBase.kt`
  - `ksrpc-core/src/commonMain/kotlin/internal/MultiChannel.kt`
- Why:
  - Frequent `Int -> String` and `String -> Int` conversions (`toString()`, `toInt()`).
- Try:
  - Introduce numeric message ID representation internally and convert only at boundaries.

### JsonRpc header transformer: avoid extra string/byte conversions
- Status: `Not tested`
- Area:
  - `ksrpc-jsonrpc/src/commonMain/kotlin/JsonRpcTransformer.kt`
- Why:
  - `encodeToString` -> `encodeToByteArray`, then `ByteArray` -> `decodeToString` -> `decodeFromString`.
- Try:
  - Explore direct byte-oriented JSON encode/decode path or pooling for reusable byte buffers.

### JsonRpc request/response dispatch parse path: reduce intermediate JSON allocations
- Status: `Not tested`
- Area:
  - `ksrpc-jsonrpc/src/commonMain/kotlin/JsonRpcWriterBase.kt`
- Why:
  - Decode to `JsonObject`/`JsonElement` first, then decode again to typed request/response.
- Try:
  - Single-pass typed decode path based on a lighter discriminator extraction.

### JNI connection bridge: cache serializers/converters used per call
- Status: `Not tested`
- Area:
  - `ksrpc-jni/src/jvmMain/kotlin/com/monkopedia/ksrpc/jni/JniConnection.kt`
- Why:
  - Repeated `Packet.serializer(JniSerialized)` and converter construction in hot methods.
- Try:
  - Cache packet serializer and int converter on instance initialization.

### JNI call-data envelope: remove nested wrapper serialization when possible
- Status: `Not tested`
- Area:
  - `ksrpc-jni/src/commonMain/kotlin/com/monkopedia/ksrpc/jni/JniSerialization.kt`
- Why:
  - Current flow wraps serialized payload in another serialized wrapper object.
- Try:
  - Evaluate flatter envelope encoding for error bit + endpoint-missing flag + payload.

#### P3

### Logging overhead in hot paths: add lazy message formatting
- Status: `Not tested`
- Area:
  - Frequent call sites in packet/channel code
  - `ksrpc-core/src/commonMain/kotlin/Logger.kt`
- Why:
  - String interpolation occurs even when default no-op logger drops logs.
- Try:
  - Add lambda-based logging API or explicit level checks before building expensive messages.

### String serializer error checks: reduce repeated serialized reads
- Status: `Not tested`
- Area:
  - `ksrpc-core/src/commonMain/kotlin/KsrpcEnvironment.kt` (`StringSerializer.isError`)
- Why:
  - `readSerialized()` called multiple times on same `CallData`.
- Try:
  - Read once and reuse local value in predicates.

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
- Status: `Not tested`
- Area:
  - `ksrpc-ktor/websocket/shared/src/commonMain/kotlin/WebsocketPacketChannel.kt`
- Why:
  - Generic converter path may add overhead per frame.
- Try:
  - Compare against direct serializer-based frame read/write with reusable buffers.
