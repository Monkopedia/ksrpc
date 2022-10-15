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
        api(project(":ksrpc-packets"))
        api(project(":ksrpc-sockets"))
        api("io.ktor:ktor-io:2.0.2")
    }
    sourceSets["jvmMain"].dependencies {
    }
    sourceSets["jvmTest"].dependencies {
    }
    sourceSets["jsTest"].dependencies {
    }
    sourceSets["jsMain"].dependencies {
    }
    sourceSets["nativeMain"].dependencies {
    }
}
