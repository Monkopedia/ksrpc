/**
 * Copyright (C) 2024 Jason Monk <monkopedia@gmail.com>
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

import com.vanniktech.maven.publish.MavenPublishBaseExtension
import java.io.File
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.signing.SigningExtension
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.dokka.gradle.DokkaTaskPartial
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
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
    supportAndroidNative: Boolean = false,
    supportIos: Boolean = supportNative,
    supportLinuxArm64: Boolean = supportNative,
    supportMingw: Boolean = supportNative,
    includePublications: Boolean = true,
    nativeConfig: KotlinNativeTarget.() -> Unit = {}
) {
    group = "com.monkopedia.ksrpc"

    plugins.apply("org.jetbrains.kotlin.multiplatform")
    plugins.apply("org.jetbrains.kotlin.plugin.serialization")

    plugins.apply("org.jetbrains.dokka")
    if (name != "ksrpc-api") {
        plugins.apply("com.monkopedia.ksrpc.plugin")
    }
    if (includePublications) {
        plugins.apply("org.gradle.maven-publish")
        plugins.apply("org.gradle.signing")
        plugins.apply("com.vanniktech.maven.publish")
    }

    val kotlin = extensions.getByType<KotlinMultiplatformExtension>()
    kotlin.apply {
        if (supportJvm) {
            jvm()
        }
        if (supportJs) {
            js(IR) {
                yarn.yarnLockAutoReplace = true
                browser {
                    testTask {
                        it.useMocha()
                    }
                }
            }
            @Suppress("OPT_IN_USAGE")
            wasmJs {
                yarn.yarnLockAutoReplace = true
                compilerOptions {
                    freeCompilerArgs.add("-Xwasm-attach-js-exception")
                }
                browser {
                    testTask {
                        it.useMocha()
                    }
                }
            }
        }

        if (supportNative) {
            macosX64 {
                binaries {}
                nativeConfig()
            }
            macosArm64 {
                binaries {}
                nativeConfig()
            }
            if (supportIos) {
                iosX64 {
                    binaries {}
                    nativeConfig()
                }
                iosArm64 {
                    binaries {}
                    nativeConfig()
                }
                iosSimulatorArm64 {
                    binaries {}
                    nativeConfig()
                }
            }

            linuxX64 {
                binaries {}
                nativeConfig()
            }
            if (supportLinuxArm64) {
                linuxArm64 {
                    binaries {}
                    nativeConfig()
                }
            }
            if (supportAndroidNative) {
                androidNativeX64 {
                    binaries {}
                    nativeConfig()
                }
                androidNativeX86 {
                    binaries {}
                    nativeConfig()
                }
                androidNativeArm64 {
                    binaries {}
                    nativeConfig()
                }
                androidNativeArm32 {
                    binaries {}
                    nativeConfig()
                }
            }
            if (supportMingw) {
                mingwX64 {
                    binaries {}
                    nativeConfig()
                }
            }
        }
        applyDefaultHierarchyTemplate()
        sourceSets["commonMain"].dependencies {
            if (name != "ksrpc-core" && name != "ksrpc-api") {
                api(project(":ksrpc-core"))
                api(project(":ksrpc-api"))
            }
        }
        sourceSets["commonTest"].dependencies {
            implementation(kotlin("test"))
        }
        if (supportJvm) {
            sourceSets["jvmMain"].dependencies {
                implementation(kotlin("stdlib"))
                implementation(kotlin("reflect"))
            }
            sourceSets["jvmTest"].dependencies {
                implementation(kotlin("test-junit"))
            }
        }
        if (supportJs) {
            sourceSets["jsMain"].dependencies {
                implementation(kotlin("stdlib"))
            }
            sourceSets["jsTest"].dependencies {
                implementation(kotlin("stdlib-js"))
                implementation(kotlin("test"))
            }
        }
        if (supportNative) {
            afterEvaluate {
                sourceSets["nativeMain"].dependencies {
                    implementation(kotlin("stdlib"))
                }
            }
        }
    }

    kotlin.targets.withType(KotlinNativeTarget::class.java) {
        it.binaries.all {
            it.binaryOptions["memoryModel"] = "experimental"
        }
    }

    tasks.withType<KotlinCompile>().all {
        it.compilerOptions {
            jvmTarget.set(JvmTarget.JVM_1_8)
            freeCompilerArgs.add("-Xskip-prerelease-check")
            freeCompilerArgs.add("-Xno-param-assertions")
        }
    }
    if (!includePublications) return

    val dokkaJavadoc = tasks.create("dokkaJavadocCustom", DokkaTask::class) {
        it.project.dependencies {
            it.plugins("org.jetbrains.dokka:kotlin-as-java-plugin:2.0.0")
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
    val publishing = extensions.getByType<MavenPublishBaseExtension>()
    val pub = extensions.getByType<PublishingExtension>()
    publishing.apply {
        pom {
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
        publishToMavenCentral()
        signAllPublications()
    }

    extensions.getByType<SigningExtension>().apply {
        useGpgCmd()
        sign(pub.publications)
    }
    project.afterEvaluate {
        tasks.withType(org.gradle.plugins.signing.Sign::class) { signingTask ->
            tasks.withType(
                org.gradle.api.publish.maven.tasks.AbstractPublishToMaven::class
            ) { publishTask ->
                publishTask.dependsOn(signingTask)
            }
        }
    }
}
