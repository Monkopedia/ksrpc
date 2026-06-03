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
package com.monkopedia.ksrpc.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

class KsrpcGradlePlugin : KotlinCompilerPluginSupportPlugin {
    override fun apply(target: Project): Unit = with(target) {
        extensions.create("ksrpc", KsrpcGradleExtension::class.java)
        checkKotlinVersion(target)
    }

    private fun checkKotlinVersion(project: Project) {
        // The compiler plugin uses FIR APIs that change between Kotlin versions; running
        // against an older Kotlin than the one we compile against throws NoClassDefFoundError
        // mid-compilation with no useful context. Fail fast with a clear message instead.
        // The minimum supported version is injected from gradle/libs.versions.toml at
        // build time, so it stays in sync as we upgrade Kotlin.
        //
        // withType(...) fires for both already-registered plugins and any that show up
        // later in the plugins block, so the check works regardless of whether the user
        // applies ksrpc before or after Kotlin (and regardless of whether they use the
        // `id(...)` apply path or the version-catalog `alias(libs.plugins.ksrpc)` path —
        // see ksrpc#185).
        project.plugins.withType(KotlinBasePlugin::class.java) { kotlinPlugin ->
            val kotlinVersion = kotlinPlugin.pluginVersion
            if (compareVersions(kotlinVersion, BuildConfig.MIN_KOTLIN_VERSION) < 0) {
                error(
                    "ksrpc compiler plugin requires Kotlin ${BuildConfig.MIN_KOTLIN_VERSION} " +
                        "or later (found $kotlinVersion). Upgrade your kotlin plugin version."
                )
            }
        }
    }

    /** Lexicographic comparison of dot-separated numeric versions (ignoring suffixes). */
    private fun compareVersions(a: String, b: String): Int {
        val aParts = a.split(".", "-").mapNotNull { it.toIntOrNull() }
        val bParts = b.split(".", "-").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(aParts.size, bParts.size)) {
            val x = aParts.getOrNull(i) ?: 0
            val y = bParts.getOrNull(i) ?: 0
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

    override fun getCompilerPluginId(): String = BuildConfig.KOTLIN_PLUGIN_ID

    // As of Kotlin 2.4 the Kotlin/Native compiler loads the same plugin artifact as the
    // JVM/embeddable path, so `KotlinCompilerPluginSupportPlugin.getPluginArtifactForNative()`
    // was removed from the interface. Native compilations now resolve the artifact below.
    override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
        groupId = BuildConfig.KOTLIN_PLUGIN_GROUP,
        artifactId = BuildConfig.KOTLIN_PLUGIN_NAME,
        version = BuildConfig.KOTLIN_PLUGIN_VERSION
    )

    override fun applyToCompilation(
        kotlinCompilation: KotlinCompilation<*>
    ): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        return project.provider {
            emptyList<SubpluginOption>()
        }
    }
}
