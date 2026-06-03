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
    id("com.github.gmazzo.buildconfig")
    alias(libs.plugins.vannik.publish)
    `signing`
}

group = "com.monkopedia.ksrpc"

// Release signing is gated so contributors can run publishToMavenLocal without the
// maintainer's GPG key. Enable in release CI via -PRELEASE_SIGNING_ENABLED=true.
val signingEnabled = (findProperty("RELEASE_SIGNING_ENABLED") as String?)?.toBoolean() == true

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

// Resolves the main build's current version from its gradle.properties so the
// test classpath references below pick up whichever jvmJar the main build just
// produced (rather than coupling to a hard-coded version that goes stale on
// every release bump).
val ksrpcVersion: String = rootDir.resolve("../gradle.properties").readLines()
    .first { it.startsWith("version=") }
    .substringAfter("=")
    .trim()

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable")

    // The locally-built jvmJars below contain the current main-build source —
    // the compiler tests need newer APIs (e.g. @KsError #77, the FlowSubservice
    // Transformer chain #39, binary adapter transformers #72/#73/#74) that the
    // last published ksrpctest version predates. Run `./gradlew jvmJar` in the
    // main build before `:compiler:ksrpc-compiler-plugin:test`; CI does this
    // explicitly in the compiler-tests job.
    testImplementation(
        files(
            rootDir.resolve("../ksrpc-api/build/libs/ksrpc-api-jvm-$ksrpcVersion.jar"),
            rootDir.resolve("../ksrpc-core/build/libs/ksrpc-core-jvm-$ksrpcVersion.jar"),
            rootDir.resolve("../ksrpc-flow/build/libs/ksrpc-flow-jvm-$ksrpcVersion.jar"),
            rootDir.resolve("../ksrpc-packets/build/libs/ksrpc-packets-jvm-$ksrpcVersion.jar"),
            rootDir.resolve(
                "../ksrpc-binary-ktor/build/libs/ksrpc-binary-ktor-jvm-$ksrpcVersion.jar"
            ),
            rootDir.resolve(
                "../ksrpc-binary-kotlinx-io/build/libs/" +
                    "ksrpc-binary-kotlinx-io-jvm-$ksrpcVersion.jar"
            ),
            rootDir.resolve(
                "../ksrpc-binary-okio/build/libs/ksrpc-binary-okio-jvm-$ksrpcVersion.jar"
            )
        )
    )
    // Published coordinate — pulls in the transitive runtime deps (kotlinx
    // serialization, coroutines, etc.) that the bare local jvmJar files above
    // don't carry. Pinned to a published version; the local jars take classpath
    // precedence for ksrpc's own types.
    testImplementation(libs.ksrpctest)
    // User-facing binary types must be resolvable at test-compile time. The
    // adapter jars above depend on these transitively, but BinaryAdapterDiagnosticTest
    // intentionally strips an adapter jar from the compile classpath and still
    // needs the user type to resolve — pull the sibling runtime jars directly.
    testImplementation(libs.kotlinx.io)
    testImplementation(libs.okio)
    testImplementation(kotlin("test-junit"))
    testImplementation("org.jetbrains.kotlin:kotlin-compiler-embeddable")
    // Exposes `SerializationComponentRegistrar` so tests that need to round-trip
    // through kotlinx-serialization (e.g. KsErrorBindingTest verifying the
    // captured `KSerializer` resolves at runtime) can chain the kotlinx.serialization
    // plugin alongside the ksrpc plugin via compile-testing. Kept out of the main
    // classpath to avoid leaking onto production builds.
    testImplementation(
        "org.jetbrains.kotlin:kotlin-serialization-compiler-plugin-embeddable"
    )
    testImplementation(libs.kotlin.compiletesting)
}

buildConfig {
    packageName("com.monkopedia.ksrpc.plugin")
    buildConfigField("String", "KOTLIN_PLUGIN_ID", "\"${rootProject.extra["kotlin_plugin_id"]}\"")
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
    publishToMavenCentral(automaticRelease = true)
    // signAllPublications() is auto-called by vanniktech when the Gradle property
    // RELEASE_SIGNING_ENABLED=true is set; we don't call it explicitly to avoid a
    // double-set on the (now finalized) underlying Property in 0.36.0.
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
        freeCompilerArgs.add("-Xjvm-default=all")
        // The FIR-phase `FirDeclarationChecker.check` overrides (issue #65) declare
        // `context(CheckerContext, DiagnosticReporter)` parameters. Context parameters
        // are on by default at the Kotlin 2.4 language version, so no opt-in flag is
        // needed (the former `-Xcontext-parameters` is now redundant).
    }
}

if (signingEnabled) {
    signing {
        useGpgCmd()
        sign(publishing.publications)
    }
}

afterEvaluate {
    tasks["generateMetadataFileForMavenPublication"].dependsOn("plainJavadocJar")
}
