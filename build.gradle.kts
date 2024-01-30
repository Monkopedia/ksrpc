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
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.base.DokkaBaseConfiguration

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

    id("org.jlleitschuh.gradle.ktlint") version "12.1.0"
    id("com.github.hierynomus.license") version "0.16.1"

    id("com.github.gmazzo.buildconfig") version "3.1.0" apply false
    id("ksrpc-generate-module")
    id("com.monkopedia.ksrpc.plugin") apply false
}

group = "com.monkopedia.ksrpc"

allprojects {
    repositories {
        mavenLocal()
        mavenCentral()
    }
}

allprojects {
    if (name == "ksrpc-compiler-plugin-native") return@allprojects
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "com.github.hierynomus.license")
    tasks.register(
        "licenseCheckForKotlin",
        com.hierynomus.gradle.license.tasks.LicenseCheck::class
    ) {
        source = fileTree(project.projectDir) { include("**/*.kt") }
    }
    tasks["license"].dependsOn("licenseCheckForKotlin")
    tasks.register(
        "licenseFormatForKotlin",
        com.hierynomus.gradle.license.tasks.LicenseFormat::class
    ) {
        source = fileTree(project.projectDir) { include("**/*.kt") }
    }
    tasks["licenseFormat"].dependsOn("licenseFormatForKotlin")

    this.license {
        header = rootProject.file("license-header.txt")
        includes(listOf("**/*.kt"))
        strictCheck = true
        ext["year"] = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        ext["name"] = "Jason Monk"
        ext["email"] = "monkopedia@gmail.com"
    }

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("0.48.0")
        android.set(true)
    }
    afterEvaluate {
        tasks.findByName("licenseCheckForKotlin")?.let {
            tasks.all {
                if ((this.name.startsWith("ktlint") && this.name.endsWith("Check")) ||
                    (this.name.startsWith("transform") && this.name.endsWith("Metadata")) ||
                    (this.name.startsWith("compile") && this.name.contains("Kotlin"))
                ) {
                    it.dependsOn(this)
                }
            }
        }
    }
}

tasks.dokkaHtmlMultiModule.configure {
    pluginConfiguration<DokkaBase, DokkaBaseConfiguration> {
        customAssets = file("dokka/assets").listFiles().toList()
        customStyleSheets = file("dokka/styles").listFiles().toList()
    }

    outputDirectory.set(buildDir.resolve("dokka"))
    rootProject.findProject(":ksrpc-packets")?.let { this.removeChildTasks(it) }
    rootProject.findProject(":ksrpc-ktor-websocket-shared")?.let { this.removeChildTasks(it) }
}
