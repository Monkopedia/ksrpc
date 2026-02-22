# ksrpc-bench

`kotlinx-benchmark` benchmarks for KSRPC hot paths and transport pathways.

## Run

- `./gradlew :ksrpc-bench:benchmark`
- `./gradlew :ksrpc-bench:jvmBenchmark`
- `./gradlew :ksrpc-bench:jsBenchmark`

## Current benchmark coverage

- `commonMain`: CallData serialization/de-serialization overhead
- `commonMain`: packet encode/decode overhead (`PacketCodecBenchmark`)
- `jvmMain`: MultiChannel allocate/send/await routing overhead
- `jvmMain`: JsonRpcWriter execute loopback overhead (transport removed)
- `jvmMain`: socket transport round-trip over packet channel (`SocketTransportBenchmark`)
- `jvmMain`: HTTP transport round-trip (`HttpTransportBenchmark`)
- `jvmMain`: websocket transport round-trip (`WebsocketTransportBenchmark`)
- `jvmMain`: JNI serialization encode/decode (`JniSerializationBenchmark`)
- `jvmMain`: JNI RPC round-trip (`JniCommunicationBenchmark`)

## Notes

- JNI benchmarks copy and load `libksrpc_test` from `:ksrpc-test` build outputs.
- JS target currently benchmarks common codec/serialization paths; browser-only service-worker transport benchmarking is not wired yet.
