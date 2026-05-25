/*
 * Copyright (C) 2026 Jason Monk <monkopedia@gmail.com>
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
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.signing.SigningExtension
import org.jetbrains.dokka.DokkaDefaults.skipEmptyPackages
import org.jetbrains.dokka.gradle.DokkaExtension
import org.jetbrains.dokka.gradle.DokkaTask
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
    supportWasm: Boolean = supportJs,
    includePublications: Boolean = true,
    applyKsrpcPlugin: Boolean = true,
    nativeConfig: KotlinNativeTarget.() -> Unit = {}
) {
    group = "com.monkopedia.ksrpc"

    plugins.apply("org.jetbrains.kotlin.multiplatform")
    plugins.apply("org.jetbrains.kotlin.plugin.serialization")

    plugins.apply("org.jetbrains.dokka")
    if (applyKsrpcPlugin && name != "ksrpc-api") {
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
        }
        if (supportWasm) {
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
        it.outputDirectory.set(File(File(project.projectDir, "build"), "javadoc"))
        it.inputs.dir("src/commonMain/kotlin")
    }
    extensions.configure<DokkaExtension> {
        dokkaSourceSets.configureEach { sourceSet ->
            sourceSet.includes.from(rootProject.file("dokka/moduledoc.md"))
            // Dokka resolves a @sample link only within the dokka source set that the
            // referencing declaration belongs to (it does not search parent source sets).
            // A given sample root may also be attached to only ONE source set per module
            // -- attaching the same root to two related source sets (e.g. common + jvm)
            // fails Dokka's pre-generation validity check ("common sample roots").
            //
            // So the attachment is chosen per module by where the @sample-bearing
            // declarations live:
            //  * Most modules reference samples only from commonMain declarations, so the
            //    common/jvm/js sample roots are attached to commonMain (JVM/JS-only
            //    samples must still compile against their platform APIs, hence the
            //    separate roots).
            //  * ksrpc-jni references samples from jvmMain (KsrpcNativeHost.connect) and
            //    nativeMain (ksrpcHostConnection) declarations, so its jvm/native roots
            //    attach to those leaf source sets instead.
            //  * ksrpc-service-worker references a JS sample from a jsMain declaration
            //    (onServiceWorkerConnection) and from a commonMain `expect`
            //    (createServiceWorkerWithConnection); attaching the JS root to jsMain
            //    resolves both (the expect resolves through its JS actual).
            val isJni = name == "ksrpc-jni"
            val isServiceWorker = name == "ksrpc-service-worker"
            if (sourceSet.name == "commonMain") {
                sourceSet.samples.from(rootProject.file("ksrpc-samples/src/commonMain/kotlin"))
                if (!isJni) {
                    sourceSet.samples.from(rootProject.file("ksrpc-samples/src/jvmMain/kotlin"))
                }
                if (!isServiceWorker) {
                    sourceSet.samples.from(rootProject.file("ksrpc-samples/src/jsMain/kotlin"))
                }
            }
            // Native samples use cinterop types (@CName, CPointer, JNIEnvVar) that only
            // resolve in a native source set, so the native sample root attaches to the
            // shared native dokka source set rather than to commonMain.
            if (sourceSet.name == "nativeMain" && isJni) {
                sourceSet.samples.from(rootProject.file("ksrpc-samples/src/nativeMain/kotlin"))
            }
            if (sourceSet.name == "jvmMain" && isJni) {
                sourceSet.samples.from(rootProject.file("ksrpc-samples/src/jvmMain/kotlin"))
            }
            if (sourceSet.name == "jsMain" && isServiceWorker) {
                sourceSet.samples.from(rootProject.file("ksrpc-samples/src/jsMain/kotlin"))
            }
            sourceSet.skipEmptyPackages.set(true)
            listOf(
                "com.monkopedia.ksrpc.internal",
                "com.monkopedia.ksrpc.jsonrpc.internal",
                "com.monkopedia.ksrpc.ktor.internal",
                "com.monkopedia.ksrpc.ktor.websocket.internal",
                "com.monkopedia.ksrpc.packets.internal",
                "com.monkopedia.ksrpc.sockets.internal"
            ).forEach { regex ->
                sourceSet.perPackageOption {
                    it.matchingRegex.set(regex)
                    it.suppress.set(true)
                }
            }
        }
    }

    val javadocJar = tasks.create("javadocJar", Jar::class) {
        it.dependsOn(dokkaJavadoc)
        it.archiveClassifier.set("javadoc")
        it.from(File(File(project.projectDir, "build"), "javadoc"))
    }
    val publishing = extensions.getByType<MavenPublishBaseExtension>()
    val pub = extensions.getByType<PublishingExtension>()
    // Release signing is gated so contributors can run publishToMavenLocal without the
    // maintainer's GPG key. Enable in release CI via -PRELEASE_SIGNING_ENABLED=true.
    val signingEnabled =
        (findProperty("RELEASE_SIGNING_ENABLED") as String?)?.toBoolean() == true
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
        publishToMavenCentral(automaticRelease = true)
        // signAllPublications() is auto-called by vanniktech when the Gradle property
        // RELEASE_SIGNING_ENABLED=true is set; we don't call it explicitly to avoid a
        // double-set on the (now finalized) underlying Property in 0.36.0.
    }

    if (signingEnabled) {
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
}
