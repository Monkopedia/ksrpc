# Changelog

## 1.1.0 (2026-05-28)

Identical in library code to 1.1.0-RC1, plus the #220 docs/test polish (JNI
connection-lifecycle guide section, KGP compiler-plugin classpath `isTransitive`
note, and a `testCloseIsIdempotent` regression test verifying existing behavior).
The 1.1.0 surface — JNI native-host API (#209), `Result<T>` `@KsMethod` returns
(#213), Kotlin/Native posix call-hang fix (#201) — was built up in the 1.1.0-RC1
cycle; see that entry below for the full feature list.

Validated against downstream consumers:

- **kplusplus** — exercised the JNI host API end-to-end from inside the
  Kotlin/Native compiler daemon (FIR-checker hosting libclang via a JNI
  connection); confirms the per-connection model, `runBlocking`-in-FIR
  ergonomics, and lifecycle.
- **hauler** — build + test + BCV apiCheck + long-form A/B microbench all
  clean across all 7 KMP targets (JVM, JS, Wasm, LinuxX64, MingwX64,
  MacosArm64/X64, IosArm64/X64/SimArm64); perf-neutral vs 1.0.0.
- **konstructor** — full Playwright e2e suite green; the script-subprocess /
  ScriptManager teardown WARN-flood pattern from the 1.0.0 cycle is gone.
- **lsp-kotlin** — `:lsp-ksrpc:build :allTests` green across JVM/JS/Wasm/Linux/
  Apple including the post-RC3 API-review tests; no regressions.

## 1.1.0-RC1 (2026-05-27)

First release candidate for 1.1.0. Additive and backward-compatible with 1.0.0 —
no breaking changes.

### New features

- **JNI native-host API** (#209): host a ksrpc service inside a Kotlin/Native
  `.so` and call it from the JVM with `KsrpcNativeHost.connect`. The consumer
  declares the binding on one of their own classes
  (`external fun initialize(host: JniHostInit)`) and backs it natively by
  delegating to `ksrpcHostConnection`; ksrpc owns the JNI export plumbing. Each
  connection gets its own native environment and service instance(s) — no global
  state and no `JNI_OnLoad`.
- **`Result<T>` `@KsMethod` return types** (#213): a `@KsMethod` may return
  `Result<O>`, equivalent to a plain `O` method wrapped in
  `runCatching`-except-cancellation, with **no wire-format change** (success
  serializes `O`; failure uses the existing `@KsError`/error envelope). `@KsError`
  participates unchanged; `CancellationException`/`TimeoutCancellationException`
  propagate rather than being captured into the `Result`. Unsupported nested
  shapes (`Result<Flow<…>>`, `Flow<Result<…>>`, `Result<@KsService>`,
  `Result<Result<…>>`) are rejected by a FIR diagnostic.

### Bug fixes

- **#201 / PR #215**: the Kotlin/Native POSIX socket transport no longer hangs a
  pending `call()` when the write side fails silently while the read side stays
  open (the Kotlin/Native analog of the #200 JVM pipe fix). Also fixes a
  reader-thread busy-loop on EOF.

### Internal / infrastructure (no consumer-facing API impact)

- **#208**: leaked serialization synthetics (`$serializer` / `$Companion` of
  `@KsrpcInternal @Serializable` types) are excluded from the BCV API dumps, so
  the dumps show only genuinely-public surface.
- **#214 / PR #218**: fixed a hang in the Kotlin/Native test suite and added a
  bounded `native-tests` CI job so native hangs fail CI fast instead of wedging.

### Documentation

- JNI + service-worker samples (#211); refreshed `ksrpc-jni` / `ksrpc-service-worker`
  module docs and README transport table (#212); `Result<T>` guide section +
  sample (#217); `@sample` wiring across public APIs (#207); honest JNI guide for
  the manual host path, since superseded by #209 (#206).

Downstream consumers (kplusplus, konstructor, lsp-kotlin, hauler) validate against
this RC.

## 1.0.0 (2026-05-21)

First stable release. API is now considered stable under semantic versioning —
breaking changes will require a 2.0.0.

Identical in library code to 1.0.0-RC6 plus the #200 fix (PR #202). The full
1.0.0 surface was built up across the RC series (see entries below):

- Service capability tier hierarchy (`RpcService` → `RpcHostService` →
  `RpcBidiService`) with compile-time FIR validation
- `@KsService` interface inheritance, `@KsContext` propagation, `@KsError`
  typed error mappings, `@KsIntrospectable` runtime metadata
- `Flow<T>` streaming (`ksrpc-flow`), binary data adapters (ktor / kotlinx-io /
  okio), generic service support
- Transports: HTTP, websocket, JSON-RPC 2.0, POSIX sockets, stdin/out,
  service-worker

Validated against four downstream consumers (konstructor, kplusplus,
lsp-kotlin, hauler) across RC2/RC4/RC5/RC6.

### Fixes since RC6

- **#200 / PR #202**: `call()` no longer hangs indefinitely when the JVM pipe
  transport's write side fails silently (e.g. a subprocess closes its stdin)
  while the read side stays open. The connection is now force-closed on
  write-side failure so pending calls fail fast instead of awaiting a response
  that can never arrive. (The Kotlin/Native posix analog is tracked as #201 for
  a 1.0.x follow-up.)

## 1.0.0-RC6 (2026-05-20)

One consumer-facing fix plus test/CI hardening. No API changes.

### Fixes

- **#195 / PR #196**: the packet receive loop now closes the multiChannel and
  binary channels cleanly when the channel was already closed by the consumer,
  instead of propagating a `CancellationException("Multi-channel failure")`
  wrapping the underlying close IOException. PR #188 (RC5) only quieted ksrpc's
  own log; the exception was still surfacing to consumers who catch and log it
  (konstructor's `ScriptManager` logged ~169 WARN events per teardown). This
  closes that propagation at the source. Reported by konstructor.

### Internal (no artifact impact)

- **#197 / PR #198**: bounded a hanging regression test
  (`CopyToAndFlushOutputCloseJvmTest`) that had been silently wedging CI since
  it was added.
- **#183 / PR #194**: split the JNI/Kotlin-Native-backed jvmTest modules into
  their own CI job with a `~/.konan` cache, cutting CI wall-clock from ~25min
  to ~5-8min.

## 1.0.0-RC5 (2026-05-19)

Pre-1.0 polish from real-world consumer feedback (konstructor, kplusplus,
lsp-kotlin, hauler) gathered during RC4 integration testing. No API changes;
also serves as a publish smoke-test for the release-signing rework in #192
before tagging 1.0.0 final.

### Fixes

- **#185 / PR #186**: Kotlin version-check gradle plugin now fires on the
  version-catalog `alias(libs.plugins.ksrpc)` apply path too, not just
  `id("...")`. Was silently skipped when ksrpc applied before Kotlin in
  the plugins block. Reported by kplusplus.
- **#187 / PR #188**: Receive-loop exceptions after `close()` log at debug
  instead of warn. Read-side counterpart to the close-during-write fix in
  PR #172 (#169). Reported by konstructor (production teardown noise).

### Developer experience

- **#189 / PR #190**: New `publishAllToMavenLocal` aggregator task that
  reaches into the compiler included build, so contributors testing against
  a development build of ksrpc no longer need to run publishToMavenLocal
  twice. Reported by kplusplus.
- **#191 / PR #192**: Release signing is now gated on the
  `RELEASE_SIGNING_ENABLED` gradle property (vanniktech's convention).
  Contributors can run `publishToMavenLocal` without the maintainer's GPG
  key. The maintainer's `signing.gnupg.keyName` moved out of the repo's
  `gradle.properties`; it now lives in `~/.gradle/gradle.properties` and
  is passed into the publish CI as a `-P` flag. Reported by kplusplus.

## 1.0.0-RC4 (2026-05-17)

Re-release of RC3 with no library code changes. RC3's main packages
(`ksrpc-core`, `ksrpc-api`, `ksrpc-flow`, etc.) failed to reach Maven Central
because the vanniktech-maven-publish plugin's auto-release polling timed out
on the compiler-plugin step, causing GitHub Actions to skip the subsequent
publish steps. Only `ksrpc-compiler-plugin` and `ksrpc-gradle-plugin` made
it through.

RC4 publishes everything from a single consistent build with the upstream fix
in place (vanniktech 0.36.0's new `VALIDATED` default replaces the old
`PUBLISHED` polling wait). Functionally equivalent to RC3 at the API level.

## 1.0.0-RC3 (2026-05-05)

### New features

- **`@KsrpcGenerated` marker annotation** (#168): compiler plugin annotates all synthetic classes (`Stub`, `Companion`, `Obj`, `ServiceExecutor`, synthesized subtype companions). Consumers using BCV can add `nonPublicMarkers += "com.monkopedia.ksrpc.annotation.KsrpcGenerated"` to filter generated classes from API dumps automatically.
- **Kotlin version check**: Gradle plugin fails fast with a clear message when applied with a Kotlin version older than the one ksrpc was compiled against. The minimum is derived from `gradle/libs.versions.toml` so it stays in sync as we upgrade.

### Bug fixes

- **JSON-RPC missing params** (#170): 0-arg `@KsMethod` calls now accept omitted `params` field (spec-allowed, used by lsp4j and other JSON-RPC clients)
- **Subprocess IOException** (#169): `copyToAndFlush` no longer escapes benign IOException to stderr when subprocess closes its stdin

### Documentation

- Migration guide explicitly states the Kotlin version requirement
- Migration guide notes that call-site code (`ksrpcEnvironment`, `asConnection`, `toStub`, etc.) needs no changes

## 1.0.0-RC2 (2026-05-03)

First working release candidate for ksrpc 1.0.0.

### New features

- **Service capability tier hierarchy**: `RpcService` → `RpcHostService` → `RpcBidiService` with compile-time validation in the FIR checker and runtime validation at registration time
- **`@KsService` interface inheritance**: child services inherit parent methods; both can have their own `@KsService` and companion
- **`@KsContext` propagation**: per-call context across all transports
  - HTTP: via request headers (`wireKey` → header name)
  - JSON-RPC: configurable conventions (`RootSiblings` default, also `RootField`, `InParams`, `TransportNative`, `None`)
  - Packet transport: via `cx` field
- **`@KsError` typed error mappings**: bidirectional code → exception type mapping with transport-native wire formats (HTTP status codes, JSON-RPC error envelope, packet error frames)
- **Binary data adapters**: separate modules for ktor `ByteReadChannel`, kotlinx-io `Source`, and okio `BufferedSource`
- **Flow streaming** (`ksrpc-flow`): `Flow<T>` in method signatures, auto-wrapped in `KsFlowService<T>`
- **Introspection** (`ksrpc-introspection`): `@KsIntrospectable` services expose endpoint metadata and schemas at runtime, including type arguments for generic sub-services
- **Generic service support**: `RpcObjectFactory` for parameterized services, plain-Kotlin subtype companion synthesis (`#95`), nested generic chains (`OuterService<T> → InnerService<T> → Flow<T>`)
- **0-argument `@KsMethod` functions**: no longer require `u: Unit` placeholder
- **Higher-level IR builder DSL**: `irConstructOf`, `irBuildListOf` helpers in the compiler plugin
- **Service worker test transport**: `SERVICE_WORKER` test type in `RpcFunctionalityTest`
- **Comprehensive documentation**: per-transport guide pages, samples, deep links to API reference

### API changes (breaking from 0.11.x)

- Services returning sub-services must extend `RpcHostService` (was `RpcService`)
- Services using `Flow` or accepting sub-service inputs must extend `RpcBidiService`
- `IntrospectableRpcService` now extends `RpcHostService` (was `RpcService`)
- Binary data requires adapter module on classpath (`ksrpc-binary-ktor`, `ksrpc-binary-kotlinx-io`, or `ksrpc-binary-okio`)
- Several `@KsrpcInternal` types removed from the public API surface (now properly gated)
- `serveHttp` reified type bound is `RpcService` (accepts all tiers; runtime check rejects bidi)
- Compiler plugin: `@KsService` on a non-interface declaration is now reported as a FIR diagnostic (was IR-time)
- `Packet` data class is `@KsrpcInternal`

See [migration-1.0.md](dokka/guides/migration-1.0.md) for detailed migration steps.

### Bug fixes

- Fixed websocket binary transport regression (-94% throughput → restored)
- Fixed packet codec regression (`encodeDefaults = false` on `PACKET_JSON`)
- Fixed JNI subtype `ClassCastException` (companion now `RpcObjectFactory` for non-generic subtypes)
- Fixed native linker errors (`DEFAULT_KSRPC_ERROR_CODE_TO_HTTP_STATUS` deduplication, duplicate `@RpcObjectKey` on synthesized companions)
- Fixed JSON-RPC spec compliance: notifications no longer send `"id": null`, success responses always include `result`
- Fixed transitive supertype validation in `@KsService` deep chains
- Fixed companion synthesis on plain-Kotlin subtypes of generic `@KsService`

### Infrastructure

- New `ksrpc-samples` module for compilable Dokka `@sample` references
- CI workflow with ktlint, license check, apiCheck, JVM tests, and compiler plugin tests
- `processDokkaGuides` task substitutes `$KSRPC_VERSION` in guide markdown
- Migrated FIR diagnostics to support IDE quick-fix infrastructure
- Auto-release enabled on Maven Central publish

### Known issues

- Consumers using BCV (binary compatibility validator) will see apiCheck failures because generated `Stub$*` classes reference internal types. Run `./gradlew apiDump` after upgrading. A future release will annotate generated synthetic classes with `@KsrpcInternal` so BCV's `nonPublicMarkers` filter excludes them automatically.

## 1.0.0-RC1 (2026-05-03)

Failed release candidate. Withdrawn due to publish workflow misconfiguration (Dokka samples and Gradle plugin portal duplicate detection). All planned changes shipped in 1.0.0-RC2.

## Earlier releases

See [GitHub Releases](https://github.com/Monkopedia/ksrpc/releases) for 0.11.x and earlier.
