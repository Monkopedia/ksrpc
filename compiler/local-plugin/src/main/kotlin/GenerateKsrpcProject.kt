/**
 * Copyright (C) 2022 Jason Monk <monkopedia@gmail.com>
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
package com.monkopedia.ksrpc.local

import java.io.File
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.maven
import org.gradle.kotlin.dsl.repositories
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.signing.SigningExtension
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.dokka.gradle.DokkaTaskPartial
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.targets.js.yarn.yarn
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

class KsrpcDummyPlugin : Plugin<Project> {
    override fun apply(project: Project) {
    }
}

fun Project.ksrpcModule(
    supportJvm: Boolean = true,
    supportJs: Boolean = true,
    supportNative: Boolean = true,
    includePublications: Boolean = true
) {
    group = "com.monkopedia.ksrpc"

    plugins.apply("org.jetbrains.kotlin.multiplatform")
    plugins.apply("org.jetbrains.kotlin.plugin.serialization")

    plugins.apply("org.jetbrains.dokka")
    plugins.apply("com.monkopedia.ksrpc.plugin")
    if (includePublications) {
        plugins.apply("org.gradle.maven-publish")
        plugins.apply("org.gradle.signing")
    }

    val kotlin = extensions.getByType<KotlinMultiplatformExtension>()
    kotlin.apply {
        if (supportJvm) {
            jvm()
        }
        if (supportJs) {
            js(IR) {
                yarn.version = "1.22.19"
                browser {
                    testTask {
                        useMocha {
                        }
                    }
                }
            }
        }

        if (supportNative) {
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
        }
        sourceSets["commonMain"].dependencies {
            if (name != "ksrpc-core") {
                api(project(":ksrpc-core"))
            }
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.4.1")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.1")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
            implementation("org.jetbrains.kotlinx:atomicfu:0.18.5")
        }
        sourceSets["commonTest"].dependencies {
            implementation(kotlin("test"))
        }
        if (supportJvm) {
            sourceSets["jvmMain"].dependencies {
                implementation("com.aventrix.jnanoid:jnanoid:2.0.0")

                implementation(kotlin("stdlib"))
                implementation(kotlin("reflect"))
                implementation("org.slf4j:slf4j-api:2.0.6")
            }
            sourceSets["jvmTest"].dependencies {
                implementation(kotlin("test-junit"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.6.4")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.4.1")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.1")
            }
        }
        if (supportJs) {
            sourceSets["jsMain"].dependencies {
                implementation(npm("nanoid", "3.1.22"))
            }
            sourceSets["jsTest"].dependencies {
                implementation(kotlin("test-js"))
            }
        }
        if (supportNative) {
            sourceSets["nativeMain"].dependencies {
                implementation(kotlin("stdlib"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
            }
        }
    }

    kotlin.targets.withType(KotlinNativeTarget::class.java) {
        it.binaries.all {
            it.binaryOptions["memoryModel"] = "experimental"
        }
    }

    tasks.withType<KotlinCompile>().all {
        it.kotlinOptions {
            jvmTarget = "1.8"
            freeCompilerArgs = freeCompilerArgs + "-Xskip-prerelease-check"
            freeCompilerArgs = freeCompilerArgs + "-Xno-param-assertions"
        }
    }
    if (!includePublications) return

    val dokkaJavadoc = tasks.create("dokkaJavadocCustom", DokkaTask::class) {
        it.project.dependencies {
            it.plugins("org.jetbrains.dokka:kotlin-as-java-plugin:1.7.20")
        }
        // outputFormat = "javadoc"
        it.outputDirectory.set(File(project.buildDir, "javadoc"))
        it.inputs.dir("src/commonMain/kotlin")
    }
    afterEvaluate {
        (tasks["dokkaHtmlPartial"] as DokkaTaskPartial).apply {
            dokkaSourceSets.configureEach {
                it.includes.from(rootProject.file("dokka/moduledoc.md").path)
                it.skipEmptyPackages.set(true)
                it.perPackageOption {
                    it.matchingRegex.set("com.monkopedia.ksrpc.internal")
                    it.suppress.set(true)
                }
                it.perPackageOption {
                    it.matchingRegex.set("com.monkopedia.ksrpc.jsonrpc.internal")
                    it.suppress.set(true)
                }
                it.perPackageOption {
                    it.matchingRegex.set("com.monkopedia.ksrpc.ktor.internal")
                    it.suppress.set(true)
                }
                it.perPackageOption {
                    it.matchingRegex.set("com.monkopedia.ksrpc.ktor.websocket.internal")
                    it.suppress.set(true)
                }
                it.perPackageOption {
                    it.matchingRegex.set("com.monkopedia.ksrpc.packets.internal")
                    it.suppress.set(true)
                }
                it.perPackageOption {
                    it.matchingRegex.set("com.monkopedia.ksrpc.sockets.internal")
                    it.suppress.set(true)
                }
            }
        }
    }

    val javadocJar = tasks.create("javadocJar", Jar::class) {
        it.dependsOn(dokkaJavadoc)
        it.archiveClassifier.set("javadoc")
        it.from(File(project.buildDir, "javadoc"))
    }
    val publishing = extensions.getByType<PublishingExtension>()
    publishing.apply {
        publications.all {
            if (it !is MavenPublication) return@all
            it.artifact(javadocJar)
            it.pom {
                it.name.set(this@ksrpcModule.name)
                it.description.set("A simple kotlin rpc library")
                it.url.set("http://www.github.com/Monkopedia/ksrpc")
                it.licenses {
                    it.license {
                        it.name.set("The Apache License, Version 2.0")
                        it.url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                it.developers {
                    it.developer {
                        it.id.set("monkopedia")
                        it.name.set("Jason Monk")
                        it.email.set("monkopedia@gmail.com")
                    }
                }
                it.scm {
                    it.connection.set("scm:git:git://github.com/Monkopedia/ksrpc.git")
                    it.developerConnection.set("scm:git:ssh://github.com/Monkopedia/ksrpc.git")
                    it.url.set("http://github.com/Monkopedia/ksrpc/")
                }
            }
        }
        repositories {
            it.maven(url = "https://oss.sonatype.org/service/local/staging/deploy/maven2/") {
                name = "OSSRH"
                credentials {
                    it.username = System.getenv("MAVEN_USERNAME")
                    it.password = System.getenv("MAVEN_PASSWORD")
                }
            }
        }
    }

    extensions.getByType<SigningExtension>().apply {
        useGpgCmd()
        sign(publishing.publications)
    }
}
