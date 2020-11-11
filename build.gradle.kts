/*
 * Copyright 2020 Jason Monk
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
import org.jetbrains.kotlin.gradle.targets.js.yarn.yarn

plugins {
    kotlin("multiplatform") version "1.4.10"
    kotlin("plugin.serialization") version "1.4.10"
    id("com.github.autostyle") version "3.1"

    java
    `maven-publish`
    `signing`
}

group = "com.monkopedia"

repositories {
    jcenter()
    mavenLocal()
    maven(url = "https://dl.bintray.com/kotlin/kotlin-dev/")
    maven(url = "https://dl.bintray.com/kotlin/kotlin-eap/")
    maven(url = "https://kotlinx.bintray.com/kotlinx/")
}

autostyle {
    kotlinGradle {
        // Since kotlin doesn't pick up on multi platform projects
        filter.include("**/*.kt")
        ktlint("0.39.0") {
            userData(mapOf("android" to "true"))
        }

        licenseHeader(
            """
            |Copyright 2020 Jason Monk
            |
            |Licensed under the Apache License, Version 2.0 (the "License");
            |you may not use this file except in compliance with the License.
            |You may obtain a copy of the License at
            |
            |    https://www.apache.org/licenses/LICENSE-2.0
            |
            |Unless required by applicable law or agreed to in writing, software
            |distributed under the License is distributed on an "AS IS" BASIS,
            |WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
            |See the License for the specific language governing permissions and
            |limitations under the License.""".trimMargin()
        )
    }
}

kotlin {
    jvm()
    js {
        yarn.version = "1.22.5"
        browser {
            testTask {
                useMocha {
                }
            }
        }
    }
    // Determine host preset.
    val hostOs = System.getProperty("os.name")

    // Create target for the host platform.
    val hostTarget = when {
        hostOs == "Mac OS X" -> macosX64("native")
        hostOs == "Linux" -> linuxX64("native")
        hostOs.startsWith("Windows") -> mingwX64("native")
        else -> throw GradleException(
            "Host OS '$hostOs' is not supported in Kotlin/Native $project."
        )
    }

    hostTarget.apply {
        binaries {
        }
    }
    sourceSets["commonMain"].dependencies {
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.0.0")
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.0.0")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.9-native-mt")
        compileOnly("io.ktor:ktor-client-core:1.4.0")
    }
    sourceSets["jvmMain"].dependencies {
        implementation(kotlin("stdlib"))
        implementation(kotlin("reflect"))
        implementation("org.slf4j:slf4j-api:1.6.1")
        compileOnly("io.ktor:ktor-server-core:1.4.0")
        compileOnly("io.ktor:ktor-server-host-common:1.4.0")
        compileOnly("io.ktor:ktor-server-netty:1.4.0")
        compileOnly("io.ktor:ktor-client-core:1.4.0")

        implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.0.0")
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.0.0")
        implementation("com.github.ajalt:clikt:2.8.0")
    }
    sourceSets["jvmTest"].dependencies {
        implementation(kotlin("test-junit"))
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.9")
        implementation("io.mockk:mockk:1.10.2")
        implementation("io.ktor:ktor-server-core:1.4.0")
        implementation("io.ktor:ktor-server-netty:1.4.0")
        implementation("io.ktor:ktor-jackson:1.4.0")

        implementation("io.ktor:ktor-client-core:1.4.0")
        implementation("io.ktor:ktor-client-okhttp:1.4.0")
    }
    sourceSets["jsTest"].dependencies {
        implementation(kotlin("test-js"))
        implementation("io.ktor:ktor-client-core:1.4.0")
        implementation("io.ktor:ktor-client-js:1.4.0")
    }
    sourceSets["jsMain"].dependencies {
        compileOnly("io.ktor:ktor-client-core:1.4.0")
        compileOnly("io.ktor:ktor-client-js:1.4.0")
    }
    sourceSets["nativeMain"].dependencies {
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.9-native-mt")
        implementation("io.ktor:ktor-client-curl:1.4.0")
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs += "-Xskip-prerelease-check"
        freeCompilerArgs += "-Xno-param-assertions"
    }
}

publishing {
    repositories {
        maven {
            name = "OSSRH"
            url = "https://oss.sonatype.org/service/local/staging/deploy/maven2/"
            credentials {
                username = System.getenv("MAVEN_USERNAME")
                password = System.getenv("MAVEN_PASSWORD")
            }
        }
    }
}
