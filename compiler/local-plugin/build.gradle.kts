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
}

group = "com.monkopedia.ksrpc"

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("gradle-plugin-api"))
    implementation(gradleKotlinDsl())
    api("org.jetbrains.kotlin:kotlin-gradle-plugin:1.7.20")
    api("org.jetbrains.dokka:dokka-gradle-plugin:1.7.20")
}

java {
    withJavadocJar()
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

gradlePlugin {
    plugins {
        create("ksrpc-generate-module") {
            id = "ksrpc-generate-module"
            implementationClass = "com.monkopedia.ksrpc.local.KsrpcDummyPlugin"
            displayName = "ksrpc-generate-module"
            description = "Plugin for helping with local build setup"
        }
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

