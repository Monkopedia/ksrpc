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
    val hostTarget = when {
        hostOs == "Mac OS X" -> "macosX64"
        hostOs == "Linux" -> "linuxX64"
        hostOs.startsWith("Windows") -> "mingwX64"
        else -> throw GradleException(
            "Host OS '$hostOs' is not supported in Kotlin/Native $project."
        )
    }
    val extension = if (hostTarget == "linuxX64") "so" else "dylib"
    dependsOn(tasks.findByName("linkDebugShared${hostTarget.capitalize()}"))
    from(buildDir.resolve("bin/$hostTarget/debugShared/libksrpc_test.$extension"))
    destinationDir = buildDir.resolve("generated/lib/resources/libs/")
    doFirst {
    }
}

afterEvaluate {
    tasks["jvmTestProcessResources"].dependsOn(copyLib)
    tasks.findByName("jsBrowserTest")?.dependsOn(tasks["jsTestTestProductionExecutableCompileSync"])
}
