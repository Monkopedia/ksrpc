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
    supportNative = false
)

kotlin {
    sourceSets["commonMain"].dependencies {
        api(project(":ksrpc-packets"))
    }
    sourceSets["jsMain"].dependencies {
    }
    sourceSets["wasmJsMain"].dependencies {
    }
}

afterEvaluate {
    // Dependency issues from the hacks inside the tests
    tasks["jsBrowserTest"].enabled = false
    tasks["wasmJsBrowserTest"].enabled = false
}
