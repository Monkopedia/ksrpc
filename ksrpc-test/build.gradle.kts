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
    includePublications = false
)

kotlin {
    sourceSets["commonTest"].dependencies {
        implementation(project(":ksrpc-core"))
        implementation(project(":ksrpc-jsonrpc"))
        implementation(project(":ksrpc-ktor-client"))
        implementation(project(":ksrpc-ktor-websocket-client"))
        implementation(project(":ksrpc-sockets"))
        implementation("io.ktor:ktor-client-core:2.0.2")
    }
    sourceSets["jvmTest"].dependencies {
        implementation(project(":ksrpc-server"))
        implementation(project(":ksrpc-ktor-server"))
        implementation(project(":ksrpc-ktor-websocket-server"))
        implementation("io.ktor:ktor-server-core:2.0.2")
        implementation("io.ktor:ktor-server-netty:2.0.2")
        implementation("io.ktor:ktor-serialization-jackson:2.0.2")

        implementation("io.ktor:ktor-client-okhttp:2.0.2")
        implementation("io.ktor:ktor-server-websockets:2.0.2")
        implementation("io.ktor:ktor-client-websockets:2.0.2")
    }
    sourceSets["nativeTest"].dependencies {
        implementation(project(":ksrpc-ktor-server"))
        implementation(project(":ksrpc-ktor-websocket-server"))
        implementation("io.ktor:ktor-server-cio:2.0.2")
    }
    sourceSets["jsTest"].dependencies {
    }
}
