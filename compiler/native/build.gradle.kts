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
    kotlin("jvm")
    alias(libs.plugins.ksp)
    id("org.jetbrains.dokka")
    alias(libs.plugins.vannik.publish)
    `signing`
}

group = "com.monkopedia.ksrpc"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-compiler")

    ksp(libs.autoservice)
    compileOnly(libs.autoservice.annotations)
}

afterEvaluate {
    tasks.named("compileKotlin") { dependsOn("syncSource") }
    tasks.named("kspKotlin") { dependsOn("syncSource") }
    tasks.named("sourcesJar") { dependsOn("syncSource") }
}
val syncSource = tasks.register<Sync>("syncSource") {
    from(project(":ksrpc-compiler-plugin").sourceSets.main.get().allSource)
    into("src/main/kotlin")
    filter {
        // Replace shadowed imports from plugin module
        when (it) {
            "import org.jetbrains.kotlin.com.intellij.mock.MockProject" ->
                "import com.intellij.mock.MockProject"

            else -> it
        }
    }
}

mavenPublishing {
    pom {
        name.set("ksrpc-compiler-plugin")
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

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

signing {
    useGpgCmd()
    sign(publishing.publications)
}
