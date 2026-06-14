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
import kotlinx.validation.ExperimentalBCVApi

buildscript {
    dependencies {
        classpath(libs.bundles.dokka)
    }
    extra["kotlin_plugin_id"] = "com.monkopedia.ksrpc.plugin"
}
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.kapt) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.dokka)
    alias(libs.plugins.kotlin.bcv)

    alias(libs.plugins.jlleitschuh.ktlint)
    alias(libs.plugins.hierynomus.license)

    alias(libs.plugins.gmazzo.buildconfig) apply false
    alias(libs.plugins.vannik.publish) apply false
    id("ksrpc-generate-module")
    id("com.monkopedia.ksrpc.plugin") apply false
}

group = "com.monkopedia.ksrpc"

allprojects {
    repositories {
        mavenCentral()
        mavenLocal()
    }
}

// == BCV setup ==
apiValidation {
    ignoredProjects.addAll(listOf("ksrpc-test", "ksrpc-bench", "ksrpc-samples", "ksrpc-service-worker-test"))
    nonPublicMarkers += "com.monkopedia.ksrpc.annotation.KsrpcInternal"
    nonPublicMarkers += "com.monkopedia.ksrpc.annotation.KsrpcGenerated"
    // kotlinx-serialization generates `$$serializer` / `$Companion` members for @Serializable
    // types. When the parent type is @KsrpcInternal (so BCV excludes it), the nonPublicMarker
    // does NOT propagate to these generated synthetics, so they leak into the JVM dump even
    // though the parent is correctly hidden. They aren't usable API (the parent is
    // inaccessible) — list them explicitly so the dump shows only genuinely-public surface.
    ignoredClasses.addAll(
        listOf(
            "com.monkopedia.ksrpc.packets.internal.Packet\$Companion",
            "com.monkopedia.ksrpc.packets.internal.Packet\$\$serializer",
            "com.monkopedia.ksrpc.jsonrpc.internal.JsonRpcError\$Companion",
            "com.monkopedia.ksrpc.jsonrpc.internal.JsonRpcError\$\$serializer",
            "com.monkopedia.ksrpc.jsonrpc.internal.JsonRpcRequest\$Companion",
            "com.monkopedia.ksrpc.jsonrpc.internal.JsonRpcRequest\$\$serializer",
            "com.monkopedia.ksrpc.jsonrpc.internal.JsonRpcResponse\$Companion",
            "com.monkopedia.ksrpc.jsonrpc.internal.JsonRpcResponse\$\$serializer",
            "com.monkopedia.ksrpc.jsonrpc.internal.JsonRpcWriterBase\$Companion"
        )
    )
    @OptIn(ExperimentalBCVApi::class)
    klib {
        enabled = true
    }
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "com.github.hierynomus.license")
    tasks.register(
        "licenseCheckForKotlin",
        com.hierynomus.gradle.license.tasks.LicenseCheck::class
    ) {
        source = fileTree(project.projectDir) {
            include("**/*.kt")
            exclude("build/**")
        }
    }
    tasks["license"].dependsOn("licenseCheckForKotlin")
    tasks.register(
        "licenseFormatForKotlin",
        com.hierynomus.gradle.license.tasks.LicenseFormat::class
    ) {
        source = fileTree(project.projectDir) {
            include("**/*.kt")
            exclude("build/**")
        }
    }
    tasks["licenseFormat"].dependsOn("licenseFormatForKotlin")

    this.license {
        header = rootProject.file("license-header.txt")
        includes(listOf("**/*.kt"))
        strictCheck = true
        useDefaultMappings = false
        mapping("kts", "PHP")
        mapping("kt", "PHP")
        ext["year"] = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        ext["name"] = "Jason Monk"
        ext["email"] = "monkopedia@gmail.com"
    }

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.8.0")
        android.set(true)
    }
}

val dokkaAssets = rootProject.file("dokka/assets").listFiles()?.toList().orEmpty()
val dokkaStyleSheets = rootProject.file("dokka/styles").listFiles()?.toList().orEmpty()

val processedGuidesDir = layout.buildDirectory.dir("dokka-guides")
val processDokkaGuides = tasks.register<Copy>("processDokkaGuides") {
    from("dokka/guides")
    into(processedGuidesDir)
    filter { it.replace("\$KSRPC_VERSION", project.version.toString()) }
}

dokka {
    dokkaPublications.named("html") {
        outputDirectory.set(projectDir.resolve("build/dokka"))
        includes.from(processedGuidesDir.map { fileTree(it) { include("*.md") } })
    }
    pluginsConfiguration.html {
        customAssets.from(dokkaAssets)
        customStyleSheets.from(dokkaStyleSheets)
    }
}

tasks.matching { it.name.startsWith("dokkaGenerate") }.configureEach {
    dependsOn(processDokkaGuides)
}

dependencies {
    dokka(project(":ksrpc-api"))
    dokka(project(":ksrpc-binary-ktor"))
    dokka(project(":ksrpc-core"))
    dokka(project(":ksrpc-introspection"))
    dokka(project(":ksrpc-jni"))
    dokka(project(":ksrpc-jsonrpc"))
    dokka(project(":ksrpc-ktor-client"))
    dokka(project(":ksrpc-ktor-server"))
    dokka(project(":ksrpc-ktor-websocket-client"))
    dokka(project(":ksrpc-ktor-websocket-server"))
    dokka(project(":ksrpc-ktor-websocket-shared"))
    dokka(project(":ksrpc-packets"))
    dokka(project(":ksrpc-server"))
    dokka(project(":ksrpc-sockets"))
    dokka(project(":ksrpc-service-worker"))
}

// Convenience aggregator so `./gradlew publishAllToMavenLocal` reaches into the compiler
// included build (settings.gradle.kts:55) — root `publishToMavenLocal` doesn't recurse, so
// the compiler-plugin / gradle-plugin / plugin-marker artifacts otherwise need a separate
// `cd compiler/ && ./gradlew publishToMavenLocal` step. Surfaced by kplusplus during the
// 1.0.0-RC4 #185 retest.
tasks.register("publishAllToMavenLocal") {
    group = "publishing"
    description = "Publishes all ksrpc artifacts (root + compiler included build) to mavenLocal."
    dependsOn("publishToMavenLocal")
    dependsOn(gradle.includedBuild("compiler").task(":publishToMavenLocal"))
}
