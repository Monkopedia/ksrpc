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
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion

class KsrpcGradlePlugin : KotlinCompilerPluginSupportPlugin {
    override fun apply(target: Project): Unit = with(target) {
        extensions.create("ksrpc", KsrpcGradleExtension::class.java)
        checkKotlinVersion(target)
    }

    private fun checkKotlinVersion(project: Project) {
        // Pull the Kotlin plugin version off the buildscript classpath. The compiler
        // plugin uses FIR APIs (FirNamedFunction) that only exist in 2.3.20+; running
        // against an older Kotlin throws NoClassDefFoundError mid-compilation with no
        // useful context. Fail fast with a clear message instead.
        val kotlinVersion = project.getKotlinPluginVersion() ?: return
        val (major, minor, patch) = kotlinVersion.split(".", "-").take(3)
            .mapNotNull { it.toIntOrNull() }
            .let {
                if (it.size < 3) listOf(0, 0, 0) else it
            }
        val ok = major > 2 || (major == 2 && minor > 3) ||
            (major == 2 && minor == 3 && patch >= 20)
        if (!ok) {
            error(
                "ksrpc compiler plugin requires Kotlin 2.3.20 or later " +
                    "(found $kotlinVersion). Upgrade your kotlin plugin version."
            )
        }
    }

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

    override fun getCompilerPluginId(): String = BuildConfig.KOTLIN_PLUGIN_ID

    override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
        groupId = BuildConfig.KOTLIN_PLUGIN_GROUP,
        artifactId = BuildConfig.KOTLIN_PLUGIN_NAME,
        version = BuildConfig.KOTLIN_PLUGIN_VERSION
    )

    @Deprecated("Deprecated in KotlinCompilerPluginSupportPlugin")
    override fun getPluginArtifactForNative(): SubpluginArtifact = SubpluginArtifact(
        groupId = BuildConfig.KOTLIN_PLUGIN_GROUP,
        artifactId = BuildConfig.KOTLIN_PLUGIN_NAME + "-native",
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
