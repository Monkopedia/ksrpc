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
    nativeConfig = {
        binaries {
            sharedLib()
        }
        compilations["main"].cinterops.create("jni") {
            val javaHome = File(System.getProperty("java.home")!!)
            packageName = "com.monkopedia.jni"
            includeDirs(
                Callable { File(javaHome, "include") },
                Callable { File(javaHome, "include/darwin") },
                Callable { File(javaHome, "include/linux") },
                Callable { File(javaHome, "include/win32") }
            )
        }
    }
)

kotlin {
    sourceSets["commonMain"].dependencies {
    }
    sourceSets["jvmMain"].dependencies {
        implementation(libs.kotlinx.serialization)
        implementation(libs.kotlinx.serialization.json)
    }
}
