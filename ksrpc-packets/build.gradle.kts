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
        implementation("io.ktor:ktor-client-core:2.0.2")
        implementation("io.ktor:ktor-client-websockets:2.0.2")
        implementation("io.ktor:ktor-http:2.0.2")
        implementation("io.ktor:ktor-serialization-kotlinx-json:2.0.2")
    }
    sourceSets["jvmMain"].dependencies {
        compileOnly("io.ktor:ktor-server-core:2.0.2")
        compileOnly("io.ktor:ktor-server-host-common:2.0.2")
        compileOnly("io.ktor:ktor-server-netty:2.0.2")
        compileOnly("io.ktor:ktor-websockets:2.0.2")
        compileOnly("io.ktor:ktor-server-websockets:2.0.2")
        compileOnly("io.ktor:ktor-client-core:2.0.2")
        compileOnly("io.ktor:ktor-server-cors:2.0.2")

        implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.3.3")
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.3")
        implementation("com.github.ajalt:clikt:2.8.0")
    }
    sourceSets["jvmTest"].dependencies {
        implementation("io.ktor:ktor-server-core:2.0.2")
        implementation("io.ktor:ktor-server-netty:2.0.2")
        implementation("io.ktor:ktor-serialization-jackson:2.0.2")

        implementation("io.ktor:ktor-client-core:2.0.2")
        implementation("io.ktor:ktor-client-okhttp:2.0.2")
        implementation("io.ktor:ktor-server-websockets:2.0.2")
        implementation("io.ktor:ktor-client-websockets:2.0.2")
    }
    sourceSets["jsTest"].dependencies {
        implementation("io.ktor:ktor-client-core:2.0.2")
    }
    sourceSets["jsMain"].dependencies {
        compileOnly("io.ktor:ktor-client-core:2.0.2")
    }
    sourceSets["nativeMain"].dependencies {
        implementation("io.ktor:ktor-client-curl:2.0.2")
    }
}