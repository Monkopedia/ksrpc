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

ksrpcModule(
    supportJs = false,
    supportLinuxArm64 = false,
    supportIos = false,
    supportMingw = false
)

kotlin {
    sourceSets["commonMain"].dependencies {
        api(project(":ksrpc-ktor-server"))
        api(project(":ksrpc-ktor-websocket-server"))
        api(project(":ksrpc-sockets"))
        api(libs.clikt)
        api(libs.ktor.server)
        api(libs.ktor.server.cors)
        api(libs.ktor.server.host.common)
    }
    sourceSets["jvmMain"].dependencies {
        api(project(":ksrpc-sockets"))
        api(libs.ktor.server.netty)
    }
    sourceSets["nativeMain"].dependencies {
        api(libs.ktor.server.cio)
    }
}
