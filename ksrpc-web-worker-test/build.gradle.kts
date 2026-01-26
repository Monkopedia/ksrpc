/*
 * Copyright 2026 Jason Monk
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
    supportJvm = false,
    supportNative = false,
    includePublications = false
)

kotlin {
    js {
        binaries.executable()
    }
    sourceSets["commonMain"].dependencies {
        api(project(":ksrpc-web-worker"))
    }
    sourceSets["jsMain"].dependencies {
        implementation(npm("webpack", "5.101.3"))
        implementation(npm("webpack-cli", "6.0.1"))
        implementation(npm("source-map-loader", "5.0.0"))
    }
}
