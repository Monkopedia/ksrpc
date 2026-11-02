# Release Process

## Before publishing a release

**Cut releases only from a `main` commit whose CI run succeeded.** `ci.yaml` runs on
every push to `main`, so an ordinary main commit has a run at its exact SHA; check it
with `gh api "repos/Monkopedia/ksrpc/actions/workflows/ci.yaml/runs?head_sha=<sha>"`.

This is a convention, and `publish.yaml` now checks it rather than assuming it — the
`verify-ref` job refuses to publish from a ref with no successful CI run. The check
exists because nothing else enforces it: `main` has no branch protection and the
repository has no rulesets, `ci.yaml` does not trigger on tags, and a release can be
created against any commitish. A tag on a commit CI never ran on would have no check
rollup at all, and an absent rollup looks the same as a passing one to anyone
eyeballing it.

A manual `workflow_dispatch` can set `skip_ci_check` to publish anyway — for a hotfix
from a ref that has no run. It has to be set deliberately, and the run logs that it
was.

### What a green CI run does and does not cover

`ci.yaml` is ubuntu-only: `lint`, `jvm-tests`, `jni-tests`, `native-tests` (linux) and
`compiler-tests`. It runs **no Apple tests** — those compile only on a PR labelled
`ci-apple`, and that job compiles without linking or running — and **no js or wasm
tests** (see #251). So a green run at the release ref is real coverage of the JVM,
Linux-native and compiler-plugin surface, and it is not coverage of every platform the
release publishes artifacts for.

Publication is irreversible: a version released to Maven Central stays released.

## After publishing a release

1. In `gradle/libs.versions.toml`, bump `ksrpctest` to the just-published version.
2. Run `./gradlew :compiler:ksrpc-compiler-plugin:test`. Expected: all tests pass against the new runtime.
3. If tests fail, investigate plugin-runtime skew. Either fix the plugin to handle both versions (soft-fallback pattern — see PR #24's `metadataSupported` check) or bump the minimum runtime requirement.
4. Commit the bump. It should be a one-line change (+ any compat fixes).

## Why this matters

The plugin's unit tests compile synthetic services against a published ksrpc-core. Keeping that pin current ensures the plugin's generated code is exercised against the runtime shape consumers will use. See #29.
