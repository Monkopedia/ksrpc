import com.monkopedia.ksrpc.local.ksrpcModule

plugins {
    kotlin("multiplatform")
}

ksrpcModule(
    supportNative = true,
    supportLinuxArm64 = false,
    supportMingw = false,
    supportJs = true,
    supportWasm = false,
    includePublications = false,
    applyKsrpcPlugin = true,
    nativeConfig = {
        binaries {
            sharedLib {
                this.export(project(":ksrpc-jni"))
            }
        }
    }
)

kotlin {
    sourceSets["commonMain"].dependencies {
        implementation(project(":ksrpc-core"))
        implementation(project(":ksrpc-flow"))
        implementation(project(":ksrpc-introspection"))
        implementation(project(":ksrpc-binary-ktor"))
    }
    sourceSets["jvmMain"].dependencies {
        implementation(project(":ksrpc-ktor-client"))
        implementation(project(":ksrpc-ktor-server"))
        implementation(project(":ksrpc-ktor-websocket-client"))
        implementation(project(":ksrpc-ktor-websocket-server"))
        implementation(project(":ksrpc-sockets"))
        implementation(project(":ksrpc-jsonrpc"))
        implementation(project(":ksrpc-server"))
        implementation(project(":ksrpc-jni"))
        implementation(libs.ktor.server)
        implementation(libs.ktor.server.netty)
        implementation(libs.ktor.client)
        implementation(libs.ktor.client.okhttp)
        implementation(libs.ktor.server.websockets)
        implementation(libs.ktor.client.websockets)
    }
    sourceSets["nativeMain"].dependencies {
        api(project(":ksrpc-jni"))
        implementation(project(":ksrpc-packets"))
    }
    sourceSets["jsMain"].dependencies {
        api(project(":ksrpc-service-worker"))
        implementation(project(":ksrpc-introspection"))
    }
}

val copyLib = tasks.register("copyLib", Copy::class) {
    val hostOs = System.getProperty("os.name")
    val arch = System.getProperty("os.arch")
    when (hostOs) {
        "Mac OS X" -> {
            if (arch == "aarch64") {
                dependsOn(tasks.getByName("linkReleaseSharedMacosArm64"))
                from(
                    projectDir.resolve(
                        "build/bin/macosArm64/releaseShared/libksrpc_samples.dylib"
                    )
                )
                destinationDir = projectDir.resolve("build/generated/lib/resources/libs/")
            } else {
                dependsOn(tasks.getByName("linkReleaseSharedMacosX64"))
                from(
                    projectDir.resolve(
                        "build/bin/macosX64/releaseShared/libksrpc_samples.dylib"
                    )
                )
                destinationDir = projectDir.resolve("build/generated/lib/resources/libs/")
            }
        }

        "Linux" -> {
            dependsOn(tasks.getByName("linkDebugSharedLinuxX64"))
            from(projectDir.resolve("build/bin/linuxX64/debugShared/libksrpc_samples.so"))
            destinationDir = projectDir.resolve("build/generated/lib/resources/libs/")
        }

        else -> {
            // Unsupported host (e.g. Windows) — skip native lib copy.
            // The task will be a no-op; the JNI sample won't link on this platform.
            enabled = false
        }
    }
}

afterEvaluate {
    // Stage the built shared library into resources so the JVM JNI sample has the
    // `.so` available, and ensure the native host sample is compiled + linked as
    // part of the module build.
    tasks.findByName("jvmMainProcessResources")?.dependsOn(copyLib)
    tasks.findByName("assemble")?.dependsOn(copyLib)
}
