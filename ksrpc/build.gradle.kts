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
import org.jetbrains.kotlin.gradle.targets.js.yarn.yarn

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.github.autostyle")
    id("org.jetbrains.dokka")
    id("com.monkopedia.ksrpc.plugin")

    java
    `maven-publish`
    // `signing`
}

group = "com.monkopedia"

kotlin {
    jvm()
    js(IR) {
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
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.1-native-mt")
        implementation("org.jetbrains.kotlinx:atomicfu:0.16.2")
        implementation("io.ktor:ktor-client-core:1.6.2")
        implementation("io.ktor:ktor-client-websockets:1.6.2")
        implementation("io.ktor:ktor-http:1.6.2")
    }
    sourceSets["commonTest"].dependencies {
        implementation(kotlin("test"))
    }
    sourceSets["jvmMain"].dependencies {
        implementation("com.aventrix.jnanoid:jnanoid:2.0.0")

        implementation(kotlin("stdlib"))
        implementation(kotlin("reflect"))
        implementation("org.slf4j:slf4j-api:1.6.1")
        compileOnly("io.ktor:ktor-server-core:1.6.2")
        compileOnly("io.ktor:ktor-server-host-common:1.6.2")
        compileOnly("io.ktor:ktor-server-netty:1.6.2")
        compileOnly("io.ktor:ktor-websockets:1.6.2")
        compileOnly("io.ktor:ktor-client-core:1.6.2")

        implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.0.0")
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.0.0")
        implementation("com.github.ajalt:clikt:2.8.0")
    }
    sourceSets["jvmTest"].dependencies {
        implementation(kotlin("test-junit"))
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.1-native-mt")
        implementation("io.ktor:ktor-server-core:1.6.2")
        implementation("io.ktor:ktor-server-netty:1.6.2")
        implementation("io.ktor:ktor-jackson:1.6.2")

        implementation("io.ktor:ktor-client-core:1.6.2")
        implementation("io.ktor:ktor-client-okhttp:1.6.2")
        implementation("io.ktor:ktor-websockets:1.6.2")
        implementation("io.ktor:ktor-client-websockets:1.6.2")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.5.1-native-mt")
    }
    sourceSets["jsTest"].dependencies {
        implementation(kotlin("test-js"))
        implementation("io.ktor:ktor-client-core:1.6.2")
    }
    sourceSets["jsMain"].dependencies {
        implementation(npm("nanoid", "3.1.22"))
        compileOnly("io.ktor:ktor-client-core:1.6.2")
    }
    sourceSets["nativeMain"].dependencies {
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.1-native-mt")
        implementation("io.ktor:ktor-client-curl:1.6.2")
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs += "-Xskip-prerelease-check"
        freeCompilerArgs += "-Xno-param-assertions"
    }
}

val dokkaJavadoc = tasks.create("dokkaJavadocCustom", org.jetbrains.dokka.gradle.DokkaTask::class) {
    dependencies {
        plugins("org.jetbrains.dokka:kotlin-as-java-plugin:1.4.10.2")
    }
    // outputFormat = "javadoc"
    outputDirectory.set(File(project.buildDir, "javadoc"))
    inputs.dir("src/commonMain/kotlin")
}

val javadocJar = tasks.create("javadocJar", Jar::class) {
    dependsOn(dokkaJavadoc)
    archiveClassifier.set("javadoc")
    from(File(project.buildDir, "javadoc"))
}

publishing {
    publications.all {
        if (this !is MavenPublication) return@all
        artifact(javadocJar)
        pom {
            name.set("ksrpc")
            description.set("A simple kotlin rpc library")
            url.set("http://www.github.com/Monkopedia/ksrpc")
            licenses {
                license {
                    name.set("The Apache License, Version 2.0")
                    url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                }
            }
            developers {
                developer {
                    id.set("monkopedia")
                    name.set("Jason Monk")
                    email.set("monkopedia@gmail.com")
                }
            }
            scm {
                connection.set("scm:git:git://github.com/Monkopedia/ksrpc.git")
                developerConnection.set("scm:git:ssh://github.com/Monkopedia/ksrpc.git")
                url.set("http://github.com/Monkopedia/ksrpc/")
            }
        }
    }
    repositories {
        maven(url = "https://oss.sonatype.org/service/local/staging/deploy/maven2/") {
            name = "OSSRH"
            credentials {
                username = System.getenv("MAVEN_USERNAME")
                password = System.getenv("MAVEN_PASSWORD")
            }
        }
    }
}

// signing {
// useGpgCmd()
// sign(publishing.publications)
// }
