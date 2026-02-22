# Optimization Log

This file tracks optimization attempts, measured impact, and final disposition.

Status values:
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
