import com.monkopedia.ksrpc.local.ksrpcModule

plugins {
    kotlin("multiplatform")
}

ksrpcModule(
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
        api(project(":ksrpc-jni"))
        implementation(libs.ktor.client)
    }
    sourceSets["nativeTest"].dependencies {
        implementation(project(":ksrpc-ktor-server"))
        implementation(project(":ksrpc-ktor-websocket-server"))
        implementation(libs.ktor.server.cio)
    }
    sourceSets["jsTest"].dependencies {
    }
}
val copyLib = tasks.register("copyLib", Copy::class) {
    dependsOn(tasks.findByName("linkDebugSharedNative"))
    from(buildDir.resolve("bin/native/debugShared/libksrpc_test.so"))
    destinationDir = buildDir.resolve("generated/lib/resources/libs/")
    doFirst {
    }
}

afterEvaluate {
    tasks["jvmTestProcessResources"].dependsOn(copyLib)
}

