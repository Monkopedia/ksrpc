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
}

ksrpcModule()

kotlin {
    sourceSets["commonMain"].dependencies {
        implementation(libs.ktor.client)
        implementation(libs.ktor.client.websockets)
        implementation(libs.ktor.http)
        implementation(libs.ktor.kotlinx.serialization)
    }
    sourceSets["jvmMain"].dependencies {
        compileOnly(libs.ktor.server)
        compileOnly(libs.ktor.server.host.common)
        compileOnly(libs.ktor.server.netty)
        compileOnly(libs.ktor.websockets)
        compileOnly(libs.ktor.server.websockets)
        compileOnly(libs.ktor.client)
        compileOnly(libs.ktor.server.cors)

        implementation(libs.kotlinx.serialization)
        implementation(libs.kotlinx.serialization.json)
    }
    sourceSets["jvmTest"].dependencies {
        implementation(libs.ktor.server)
        implementation(libs.ktor.server.netty)
        implementation(libs.ktor.jackson.serialization)

        implementation(libs.ktor.client)
        implementation(libs.ktor.client.okhttp)
        implementation(libs.ktor.server.websockets)
        implementation(libs.ktor.client.websockets)
    }
    sourceSets["jsTest"].dependencies {
        implementation(libs.ktor.client)
    }
    sourceSets["jsMain"].dependencies {
        compileOnly(libs.ktor.client)
    }
    sourceSets["nativeMain"].dependencies {
        implementation(libs.ktor.client.curl)
    }
}
