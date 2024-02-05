import com.monkopedia.ksrpc.local.ksrpcModule

plugins {
    kotlin("multiplatform")
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

kotlin {
    sourceSets["commonTest"].dependencies {
        implementation(project(":ksrpc-core"))
        implementation(project(":ksrpc-jsonrpc"))
        implementation(project(":ksrpc-ktor-client"))
        implementation(project(":ksrpc-ktor-websocket-client"))
        implementation(project(":ksrpc-sockets"))
        implementation(project(":ksrpc-packets"))
        implementation(libs.ktor.client)
    }
    sourceSets["jvmTest"].resources.srcDir(buildDir.resolve("generated/lib/resources"))
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
        implementation(project(":ksrpc-ktor-server"))
        implementation(project(":ksrpc-ktor-websocket-server"))
        implementation(libs.ktor.server.cio)
    }
    sourceSets["jsTest"].dependencies {
        implementation(kotlin("stdlib"))
        implementation(kotlin("test"))
    }
}
val copyLib = tasks.register("copyLib", Copy::class) {
    val hostOs = System.getProperty("os.name")
    val arch = System.getProperty("os.arch")
    when {
        hostOs == "Mac OS X" -> {
            if (arch == "aarch64") {
                dependsOn(tasks.findByName("linkReleaseSharedMacosArm64"))
                from(buildDir.resolve("bin/macosArm64/releaseShared/libksrpc_test.dylib"))
                destinationDir = buildDir.resolve("generated/lib/resources/libs/")
            } else {
                dependsOn(tasks.findByName("linkReleaseSharedMacosX64"))
                from(buildDir.resolve("bin/macosX64/releaseShared/libksrpc_test.dylib"))
                destinationDir = buildDir.resolve("generated/lib/resources/libs/")
            }
        }
        hostOs == "Linux" -> {
            dependsOn(tasks.findByName("linkDebugSharedLinuxX64"))
            from(buildDir.resolve("bin/linuxX64/debugShared/libksrpc_test.so"))
            destinationDir = buildDir.resolve("generated/lib/resources/libs/")
        }
        else -> throw GradleException(
            "Host OS '$hostOs' is not supported in Kotlin/Native $project."
        )
    }
    doFirst {
    }
}

afterEvaluate {
    tasks["jvmTestProcessResources"].dependsOn(copyLib)
    tasks.findByName("jsBrowserTest")?.dependsOn(tasks["jsTestTestProductionExecutableCompileSync"])
}
