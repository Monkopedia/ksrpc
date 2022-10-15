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
    supportNative = false
)

kotlin {
    sourceSets["jvmMain"].dependencies {
        api(project(":ksrpc-ktor-server"))
        api(project(":ksrpc-ktor-websocket-server"))
        api(project(":ksrpc-sockets"))
        implementation("com.github.ajalt:clikt:2.8.0")
        implementation("io.ktor:ktor-server-netty:2.0.2")
        implementation("io.ktor:ktor-server-cors:2.0.2")
    }
}
