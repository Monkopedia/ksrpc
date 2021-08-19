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
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java-gradle-plugin")
    kotlin("jvm")
    id("com.github.gmazzo.buildconfig")
    `maven-publish`
    `signing`
}

group = "com.monkopedia"

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("gradle-plugin-api"))
}

gradlePlugin {
    plugins {
        create("ksrpc-gradle-plugin") {
            id = rootProject.extra["kotlin_plugin_id"].toString()
            implementationClass = "com.monkopedia.ksrpc.gradle.KsrpcGradlePlugin"
        }
    }
}

buildConfig {
    val project = project(":ksrpc-compiler-plugin")
    packageName("com.monkopedia.ksrpc.gradle")
    buildConfigField("String", "KOTLIN_PLUGIN_ID", "\"${rootProject.extra["kotlin_plugin_id"]}\"")
    buildConfigField("String", "KOTLIN_PLUGIN_GROUP", "\"${project.group}\"")
    buildConfigField("String", "KOTLIN_PLUGIN_NAME", "\"${project.name}\"")
    buildConfigField("String", "KOTLIN_PLUGIN_VERSION", "\"${project.version}\"")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

signing {
   useGpgCmd()
   sign(publishing.publications)
}
