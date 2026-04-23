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
    id("com.github.gmazzo.buildconfig")
    alias(libs.plugins.vannik.publish)
    `signing`
}

group = "com.monkopedia.ksrpc"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable")

    ksp(libs.autoservice)
    compileOnly(libs.autoservice.annotations)

    // Locally-built `ksrpc-api` jvmJar, listed BEFORE the published `ksrpctest`
    // coordinate so the newly-added `@KsError` annotation (#77, Part 2 of #13)
    // is visible to the test sources. The published 0.11.1 ksrpc-api coordinate
    // predates the annotation.
    testImplementation(
        files(
            project.rootDir.resolve("../ksrpc-api/build/libs/ksrpc-api-jvm-0.11.1.jar")
        )
    )
    // Locally-built `ksrpc-core` jvmJar, listed BEFORE the published `ksrpctest`
    // coordinate so the local (non-sealed) `Transformer` interface takes
    // precedence over the published (sealed) one. The FlowSupportTest (#39) needs
    // this because `FlowTransformer` in the locally-built `ksrpc-flow` extends
    // `Transformer`; the published 0.11.1 sealed version would reject the
    // extension at user-code compile time.
    testImplementation(
        files(
            project.rootDir.resolve("../ksrpc-core/build/libs/ksrpc-core-jvm-0.11.1.jar")
        )
    )
    testImplementation(libs.ksrpctest)
    // Locally-built `ksrpc-flow` jvmJar — see FlowSupportTest (#39). The compiler
    // plugin sits in an included build, so `project(":ksrpc-flow")` cannot reach
    // the main build's module; we stitch the jvmJar file onto the test classpath
    // by path. Consumers must run `:ksrpc-flow:jvmJar` (and `:ksrpc-core:jvmJar`)
    // in the main build before `:compiler:ksrpc-compiler-plugin:test`; CI already
    // does this implicitly because the plugin tests run after the main build.
    testImplementation(
        files(
            project.rootDir.resolve("../ksrpc-flow/build/libs/ksrpc-flow-jvm-0.11.1.jar")
        )
    )
    // Locally-built `ksrpc-packets` — required transitively by `ksrpc-binary-ktor`
    // for plugin tests that compile `ByteReadChannel` signatures.
    testImplementation(
        files(
            project.rootDir.resolve("../ksrpc-packets/build/libs/ksrpc-packets-jvm-0.11.1.jar")
        )
    )
    // Locally-built `ksrpc-binary-ktor` — hosts the `ByteReadChannelTransformer`
    // the plugin emits for `ByteReadChannel` service signatures (issue #72).
    testImplementation(
        files(
            project.rootDir.resolve(
                "../ksrpc-binary-ktor/build/libs/ksrpc-binary-ktor-jvm-0.11.1.jar"
            )
        )
    )
    // Locally-built `ksrpc-binary-kotlinx-io` — hosts the `SourceTransformer`
    // the plugin emits for `kotlinx.io.Source` service signatures (issue #73).
    testImplementation(
        files(
            project.rootDir.resolve(
                "../ksrpc-binary-kotlinx-io/build/libs/" +
                    "ksrpc-binary-kotlinx-io-jvm-0.11.1.jar"
            )
        )
    )
    // Locally-built `ksrpc-binary-okio` — hosts the `BufferedSourceTransformer`
    // the plugin emits for `okio.BufferedSource` service signatures (issue #74).
    testImplementation(
        files(
            project.rootDir.resolve(
                "../ksrpc-binary-okio/build/libs/ksrpc-binary-okio-jvm-0.11.1.jar"
            )
        )
    )
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

afterEvaluate {
    tasks["generateMetadataFileForMavenPublication"].dependsOn("plainJavadocJar")
    tasks["kspKotlin"].dependsOn("generateBuildConfigClasses")
}
