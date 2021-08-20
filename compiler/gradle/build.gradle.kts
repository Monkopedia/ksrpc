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

java {
    withJavadocJar()
    withSourcesJar()
}

gradlePlugin {
    plugins {
        create("ksrpc-gradle-plugin") {
            id = rootProject.extra["kotlin_plugin_id"].toString()
            implementationClass = "com.monkopedia.ksrpc.gradle.KsrpcGradlePlugin"
            displayName = "ksrpc-gradle-plugin"
            description = "A simple kotlin rpc library"
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

publishing {
    publications.all {
        if (this !is MavenPublication) return@all
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
    }
    repositories {
        maven(url = "https://oss.sonatype.org/service/local/staging/deploy/maven2/") {
            name = "OSSRH"
            credentials {
                username = System.getenv("MAVEN_USERNAME")
                password = System.getenv("MAVEN_PASSWORD")
            }
        }
    }
}

signing {
   useGpgCmd()
   sign(publishing.publications)
}
