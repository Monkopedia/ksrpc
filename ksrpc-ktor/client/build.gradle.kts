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

ksrpcModule(supportLinuxArm64 = false)

kotlin {
    sourceSets["commonMain"].dependencies {
        implementation(libs.ktor.client)
        implementation(libs.ktor.http)
        implementation(libs.ktor.kotlinx.serialization)
    }
    sourceSets["jvmMain"].dependencies {
        compileOnly(libs.ktor.client)

        implementation(libs.kotlinx.serialization)
        implementation(libs.kotlinx.serialization.json)
    }
    sourceSets["jsMain"].dependencies {
        compileOnly(libs.ktor.client)
    }
    if (System.getProperty("os.name") == "Linux") {
        sourceSets["linuxMain"].dependencies {
            implementation(libs.ktor.client.curl)
        }
    }
    sourceSets["mingwMain"].dependencies {
        implementation(libs.ktor.client.curl)
    }
    sourceSets["macosMain"].dependencies {
        implementation(libs.ktor.client.darwin)
    }
    sourceSets["iosMain"].dependencies {
        implementation(libs.ktor.client.darwin)
    }
}
