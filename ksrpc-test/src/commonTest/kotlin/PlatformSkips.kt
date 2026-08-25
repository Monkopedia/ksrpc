/*
 * Copyright (C) 2026 Jason Monk <monkopedia@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.monkopedia.ksrpc

/**
 * Prefix on every skip marker, so the no-ops can be counted from the result XML with
 * `grep -c KSRPC-SKIP` and located in source with `grep -rn skipUnsupported`.
 */
const val SKIP_MARKER = "KSRPC-SKIP"

/**
 * Records that [test] did nothing on this platform, and why.
 *
 * `kotlin.test` has no portable skip. A test that returns early therefore reports as
 * **passed** while asserting nothing, so on its own a green line means either "verified" or
 * "was not applicable here" and nothing distinguishes them — the executed test count
 * overstates coverage by however many guards fired.
 *
 * The marker lands in the `system-out` of the result XML, including on browser targets, so
 * the no-ops are countable after the fact:
 *
 * ```
 * grep -rho 'KSRPC-SKIP [A-Za-z0-9_]*' build/test-results/<task> | sort | uniq -c
 * ```
 *
 * The character class must include `_` and digits: every guard in the browser-only
 * `ServiceWorkerTest` is named like `wasmServiceWorkerConnection_methods`, and `[a-zA-Z]*`
 * truncates all seven of them to one `wasmServiceWorkerConnection` bucket — a report that
 * looks complete while merging distinct things, which is the defect this whole mechanism
 * exists to expose.
 *
 * On wasm that reports 48 no-ops out of 395 tests across 15 classes — three quarters of
 * them `testHttpPath`, `testWebsocketPath` and `testServiceWorkerPassthrough` from
 * [RpcFunctionalityTest]. It does not appear in the Gradle console output for browser runs,
 * only in the XML. **The 395 is not stable**: that suite reports between 388 and 395 tests
 * on an unchanged commit (issue #255), so treat the ratio as approximate and re-measure
 * rather than trusting this number.
 *
 * Turning a fired guard into a *failure* when the platform claims to support the capability
 * would be a stronger signal, but changes the pass/fail behaviour of a base class shared by
 * 20 test classes — deliberately out of scope here (issue #261).
 */
fun skipUnsupported(test: String, reason: String) {
    println("$SKIP_MARKER $test — $reason")
}
