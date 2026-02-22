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

tasks.matching {
    it.name == "ktlintJsJsBenchmarkSourceSetCheck" ||
        it.name == "ktlintJsJsBenchmarkSourceSetFormat" ||
        it.name == "runKtlintCheckOverJsJsBenchmarkSourceSet" ||
        it.name == "runKtlintFormatOverJsJsBenchmarkSourceSet"
}.configureEach {
    enabled = false
}
