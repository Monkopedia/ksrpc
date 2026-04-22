# Release Process

## After publishing a release

1. In `gradle/libs.versions.toml`, bump `ksrpctest` to the just-published version.
2. Run `./gradlew :compiler:ksrpc-compiler-plugin:test`. Expected: all tests pass against the new runtime.
3. If tests fail, investigate plugin-runtime skew. Either fix the plugin to handle both versions (soft-fallback pattern — see PR #24's `metadataSupported` check) or bump the minimum runtime requirement.
4. Commit the bump. It should be a one-line change (+ any compat fixes).

## Why this matters

The plugin's unit tests compile synthetic services against a published ksrpc-core. Keeping that pin current ensures the plugin's generated code is exercised against the runtime shape consumers will use. See #29.
