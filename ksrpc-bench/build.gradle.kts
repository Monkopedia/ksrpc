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
plugins {
    kotlin("multiplatform")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlinx.benchmark)
}

kotlin {
    jvm()
    js(IR) {
        nodejs()
    }
    linuxX64 {
        binaries {
            executable("posixBench") {
                entryPoint = "com.monkopedia.ksrpc.bench.posixBenchMain"
            }
        }
    }

    sourceSets["commonMain"].dependencies {
        implementation(project(":ksrpc-core"))
        implementation(project(":ksrpc-jsonrpc"))
        implementation(project(":ksrpc-packets"))
        implementation(libs.kotlinx.coroutines)
        implementation(libs.kotlinx.serialization.json)
        implementation(libs.kotlinx.benchmark.runtime)
    }

    sourceSets["jvmMain"].resources.srcDir(projectDir.resolve("build/generated/lib/resources"))
    sourceSets["jvmMain"].dependencies {
        implementation(project(":ksrpc-jni"))
        implementation(project(":ksrpc-binary-ktor"))
        implementation(project(":ksrpc-sockets"))
        implementation(project(":ksrpc-ktor-client"))
        implementation(project(":ksrpc-ktor-server"))
        implementation(project(":ksrpc-ktor-websocket-client"))
        implementation(project(":ksrpc-ktor-websocket-server"))
        implementation(libs.ktor.client.okhttp)
        implementation(libs.ktor.client.websockets)
        implementation(libs.ktor.server.netty)
        implementation(libs.ktor.server.websockets)
        implementation(libs.ktor.kotlinx.serialization)
    }

    sourceSets["linuxX64Main"].dependencies {
        implementation(project(":ksrpc-sockets"))
    }
}

benchmark {
    targets {
        register("jvm")
        register("js")
    }
}

val copyJniBenchLib = tasks.register("copyJniBenchLib", Copy::class) {
    dependsOn(":ksrpc-test:copyLib")
    from(project(":ksrpc-test").layout.buildDirectory.dir("generated/lib/resources/libs"))
    destinationDir = projectDir.resolve("build/generated/lib/resources/libs")
}

tasks.matching {
    it.name.contains("jvm", ignoreCase = true) && it.name.endsWith("ProcessResources")
}.configureEach {
    dependsOn(copyJniBenchLib)
}

// kotlinx-benchmark generates a js source set into build/benchmarks/js/sources, and ktlint
// registers check/format tasks for it like any other. Those tasks lint generated code: running
// them reports hundreds of indent, wrapping and class-naming violations in files this repo does
// not author and cannot fix, and `ktlintCheck` fails on them.
//
// They are disabled outright rather than excluded by path — a `filter { exclude … }` on the
// KtlintExtension also works, but the entire source set is generated, so there is nothing in it
// that a narrower filter would usefully keep linting.
//
// Do not read this as the shape #285 was about — a check switched off over sources someone
// could fix. Removing the block makes `:ksrpc-bench:ktlintCheck` fail on 190 violations (at
// the time of writing) across four files, all under
// `build/benchmarks/js/sources/kotlinx/benchmark/generated/`, and on nothing else. CI never
// generates these sources, so nothing will notice that count drifting.
//
// To re-measure that, delete `build/reports/ktlint` and `build/intermediates/ktLint` first,
// because a plain re-check can report without measuring anything. With the block removed and
// nothing deleted, the producer task is not scheduled at all and
// `ktlintJsJsBenchmarkSourceSetCheck` fails off whatever
// `runKtlintCheckOverJsJsBenchmarkSourceSet_errors.bin` is already on disk — the right answer
// only if the file happens to be current. `--rerun-tasks` does force the producer to run once
// the block is removed, so it is not wrong; deleting is simply the step that holds either way.
//
// Do not use this block's own presence to test that: with the block in place both tasks are
// SKIPPED, so nothing reads or rewrites that file, and its timestamp says nothing about what
// a re-check would do.
//
// Unverified: the build cache was off throughout. A populated remote cache could in principle
// restore a deleted `.bin`, which would put a re-measurer back in the stale state this step
// exists to avoid.
//
// Whether the block has any effect also depends on build state: the generated sources exist
// only after the benchmark generation task has run, so on a clean tree these tasks are
// NO-SOURCE and removing the block looks harmless.
tasks.matching {
    it.name == "ktlintJsJsBenchmarkSourceSetCheck" ||
        it.name == "ktlintJsJsBenchmarkSourceSetFormat" ||
        it.name == "runKtlintCheckOverJsJsBenchmarkSourceSet" ||
        it.name == "runKtlintFormatOverJsJsBenchmarkSourceSet"
}.configureEach {
    enabled = false
}
