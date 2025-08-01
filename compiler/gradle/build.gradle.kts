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
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java-gradle-plugin")
    kotlin("jvm")
    id("com.github.gmazzo.buildconfig")
    alias(libs.plugins.gradle.publish)
    alias(libs.plugins.vannik.publish)
    `signing`
}

group = "com.monkopedia.ksrpc"

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("gradle-plugin-api"))
}

java {
    withJavadocJar()
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

gradlePlugin {
    website = "https://github.com/monkopedia/ksrpc"
    vcsUrl = "https://github.com/monkopedia/ksrpc"

    plugins.create("ksrpc-gradle-plugin") {
        id = rootProject.extra["kotlin_plugin_id"]?.toString() ?: "com.monkopedia.ksrpc.plugin"
        implementationClass = "com.monkopedia.ksrpc.gradle.KsrpcGradlePlugin"
        displayName = "KSRPC Gradle Plugin"
        description = "A simple kotlin rpc library"
        tags = listOf("kotlin", "kotlin/native", "kotlin/js", "kotlin/jvm", "json", "rpc")
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
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
    }
}

mavenPublishing {
    pom {
        name.set("ksrpc-gradle-plugin")
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
    publishToMavenCentral()
    signAllPublications()
}

signing {
    useGpgCmd()
    sign(publishing.publications)
}
