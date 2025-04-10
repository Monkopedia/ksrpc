/*
 * Copyright 2021 Jason Monk
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
import com.monkopedia.ksrpc.local.ksrpcModule

plugins {
    kotlin("multiplatform")
    alias(libs.plugins.kotlin.atomicfu)
}

ksrpcModule()

kotlin {
    sourceSets["commonMain"].dependencies {
        api(libs.ktor.io)
        api(libs.kotlinx.serialization)
        api(libs.kotlinx.serialization.json)
        api(libs.kotlinx.coroutines)
        api(kotlin("stdlib"))
        api(project(":ksrpc-api"))
    }
    sourceSets["jvmMain"].dependencies {
        implementation(libs.jnanoid)
        implementation(libs.slf4j.api)
    }
    sourceSets["jsMain"].dependencies {
        implementation(npm("nanoid", libs.versions.nanoid.get()))
    }
    sourceSets["wasmJsMain"].dependencies {
        implementation(npm("nanoid", libs.versions.nanoid.get()))
    }
//    linuxX64("native") {
//        binaries {
//        }
//    }
}
