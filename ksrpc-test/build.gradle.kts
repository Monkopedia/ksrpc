@file:OptIn(ExternalKotlinTargetApi::class)

import com.monkopedia.ksrpc.local.ksrpcModule
import org.jetbrains.kotlin.gradle.ExternalKotlinTargetApi
import org.jetbrains.kotlin.gradle.targets.js.testing.karma.KarmaConfig

plugins {
    kotlin("multiplatform")
    alias(libs.plugins.kotlin.atomicfu)
}

ksrpcModule(
    supportLinuxArm64 = false,
    supportMingw = false,
    includePublications = false,
    nativeConfig = {
        binaries {
            sharedLib {
                this.export(project(":ksrpc-jni"))
            }
        }
    }
)

data class KarmaFileConfig(
    val pattern: String,
    val included: Boolean = true,
    val served: Boolean = true,
    val watched: Boolean = false
)
kotlin {
    js {
        browser {
            testTask {
                useKarma {
                    val config = this::class.java.getDeclaredField("config").also {
                        it.isAccessible = true
                    }.get(this) as KarmaConfig

                    config.files.add(
                        KarmaFileConfig(
                            pattern = rootProject.projectDir.absolutePath +
                                "/build/js/packages/ksrpc-ksrpc-test-test/kotlin/ksrpc-service-worker-test.js",
                            included = false,
                            served = true,
                            watched = true
                        )
                    )
                    useChromeHeadless()
                }
            }
        }
    }
    wasmJs {
        browser {
            testTask {
                useKarma {
                    val config = this::class.java.getDeclaredField("config").also {
                        it.isAccessible = true
                    }.get(this) as KarmaConfig

                    config.files.add(
                        KarmaFileConfig(
                            pattern = rootProject.projectDir.absolutePath +
                                "/build/wasm/packages/ksrpc-ksrpc-test-test/kotlin/ksrpc-service-worker-test.js",
                            included = false,
                            served = true,
                            watched = true
                        )
                    )
                    useChromeHeadless()
                }
            }
        }
    }
    sourceSets["commonMain"].dependencies {
        implementation(project(":ksrpc-introspection"))
    }
    sourceSets["commonTest"].dependencies {
        implementation(project(":ksrpc-core"))
        implementation(project(":ksrpc-jsonrpc"))
        implementation(project(":ksrpc-ktor-client"))
        implementation(project(":ksrpc-ktor-websocket-client"))
        implementation(project(":ksrpc-sockets"))
        implementation(project(":ksrpc-packets"))
        implementation(project(":ksrpc-introspection"))
        implementation(libs.ktor.client)
    }
    sourceSets["jvmTest"].resources.srcDir(projectDir.resolve("build/generated/lib/resources"))
    sourceSets["jvmTest"].dependencies {
        implementation(project(":ksrpc-server"))
        implementation(project(":ksrpc-jni"))
        implementation(project(":ksrpc-ktor-server"))
        implementation(project(":ksrpc-ktor-websocket-server"))
        implementation(libs.ktor.server)
        implementation(libs.ktor.server.netty)
        implementation(libs.ktor.jackson.serialization)

        implementation(libs.ktor.client.okhttp)
        implementation(libs.ktor.server.websockets)
        implementation(libs.ktor.client.websockets)
    }
    sourceSets["nativeMain"].dependencies {
        implementation(project(":ksrpc-packets"))
        api(project(":ksrpc-jni"))
        implementation(libs.ktor.client)
    }
    sourceSets["nativeTest"].dependencies {
        api(project(":ksrpc-jni"))
        implementation(project(":ksrpc-ktor-server"))
        implementation(project(":ksrpc-ktor-websocket-server"))
        implementation(libs.ktor.server.cio)
    }
    sourceSets["jsMain"].dependencies {
        implementation(devNpm("copy-webpack-plugin", "13.0.1"))
    }
    sourceSets["jsTest"].dependencies {
        implementation(project(":ksrpc-service-worker-test"))
        implementation(kotlin("stdlib"))
        implementation(kotlin("test"))
    }
    sourceSets["jsTest"].resources.srcDir(projectDir.resolve("build/generated/service-worker/js"))
    sourceSets["wasmJsTest"].dependencies {
        implementation(project(":ksrpc-service-worker-test"))
        implementation(kotlin("stdlib"))
        implementation(kotlin("test"))
    }
    sourceSets["wasmJsTest"].resources.srcDir(projectDir.resolve("build/generated/service-worker/wasm"))
}
val copyLib = tasks.register("copyLib", Copy::class) {
    val hostOs = System.getProperty("os.name")
    val arch = System.getProperty("os.arch")
    when (hostOs) {
        "Mac OS X" -> {
            if (arch == "aarch64") {
                dependsOn(tasks.getByName("linkReleaseSharedMacosArm64"))
                from(projectDir.resolve("build/bin/macosArm64/releaseShared/libksrpc_test.dylib"))
                destinationDir = projectDir.resolve("build/generated/lib/resources/libs/")
            } else {
                dependsOn(tasks.getByName("linkReleaseSharedMacosX64"))
                from(projectDir.resolve("build/bin/macosX64/releaseShared/libksrpc_test.dylib"))
                destinationDir = projectDir.resolve("build/generated/lib/resources/libs/")
            }
        }

        "Linux" -> {
            dependsOn(tasks.getByName("linkDebugSharedLinuxX64"))
            from(projectDir.resolve("build/bin/linuxX64/debugShared/libksrpc_test.so"))
            destinationDir = projectDir.resolve("build/generated/lib/resources/libs/")
        }

        else -> throw GradleException(
            "Host OS '$hostOs' is not supported in Kotlin/Native $project."
        )
    }
    doFirst {
    }
}

val copyWebWorkerJs = tasks.register("copyWebWorkerJs", Copy::class) {
    dependsOn(
        project(":ksrpc-service-worker-test").tasks.matching {
            it.name.startsWith("js") && it.name.endsWith("BrowserDistribution")
        }
    )
    from(
        project(":ksrpc-service-worker-test").layout.buildDirectory.dir("dist/js/productionExecutable")
    ) {
        include("*.js")
        include("*.map")
    }
    destinationDir = projectDir.resolve("build/generated/service-worker/js")
}

val copyWebWorkerWasm = tasks.register("copyWebWorkerWasm", Copy::class) {
    dependsOn(
        project(":ksrpc-service-worker-test").tasks.matching {
            it.name.startsWith("js") && it.name.endsWith("BrowserDistribution")
        }
    )
    from(
        project(":ksrpc-service-worker-test").layout.buildDirectory.dir("dist/js/productionExecutable")
    ) {
        include("*.js")
        include("*.map")
    }
    destinationDir = projectDir.resolve("build/generated/service-worker/wasm")
}

afterEvaluate {
    tasks["jvmTestProcessResources"].dependsOn(copyLib)
    tasks.findByName("jsTestProcessResources")?.dependsOn(copyWebWorkerJs)
    tasks.findByName("wasmJsTestProcessResources")?.dependsOn(copyWebWorkerWasm)
    val jsBrowserTest = tasks["jsBrowserTest"]
    jsBrowserTest.dependsOn(project(":ksrpc-service-worker-test").tasks["jsProductionExecutableCompileSync"])
    jsBrowserTest.mustRunAfter(project(":ksrpc-service-worker-test").tasks["jsProductionExecutableCompileSync"])
    jsBrowserTest.dependsOn(copyWebWorkerJs)
}
