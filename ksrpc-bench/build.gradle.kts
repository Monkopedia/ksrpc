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
// could fix. Removing the block makes `:ksrpc-bench:ktlintCheck` fail on 190 violations across
// four files, all under `build/benchmarks/js/sources/kotlinx/benchmark/generated/`, and on
// nothing else.
//
// To re-measure that, delete `build/reports/ktlint` and `build/intermediates/ktLint` first.
// `--rerun-tasks` is NOT enough and gives a wrong answer: it does not clear
// `runKtlintCheckOverJsJsBenchmarkSourceSet_errors.bin`, and while this block is in place
// nothing rewrites that file, so a consumer reads a result from whenever the task last ran.
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
