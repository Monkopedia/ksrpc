import com.monkopedia.ksrpc.local.ksrpcModule

plugins {
    kotlin("multiplatform")
}

ksrpcModule(
    includePublications = false
)

kotlin {
    sourceSets["commonTest"].dependencies {
        implementation(project(":ksrpc-core"))
        implementation(project(":ksrpc-jsonrpc"))
        implementation(project(":ksrpc-ktor-client"))
        implementation(project(":ksrpc-ktor-websocket-client"))
        implementation(project(":ksrpc-sockets"))
        implementation("io.ktor:ktor-client-core:2.0.2")
    }
    sourceSets["jvmTest"].dependencies {
        implementation(project(":ksrpc-server"))
        implementation(project(":ksrpc-ktor-server"))
        implementation(project(":ksrpc-ktor-websocket-server"))
        implementation("io.ktor:ktor-server-core:2.0.2")
        implementation("io.ktor:ktor-server-netty:2.0.2")
        implementation("io.ktor:ktor-serialization-jackson:2.0.2")

        implementation("io.ktor:ktor-client-okhttp:2.0.2")
        implementation("io.ktor:ktor-server-websockets:2.0.2")
        implementation("io.ktor:ktor-client-websockets:2.0.2")
    }
    sourceSets["nativeTest"].dependencies {
        implementation(project(":ksrpc-ktor-server"))
        implementation(project(":ksrpc-ktor-websocket-server"))
        implementation("io.ktor:ktor-server-cio:2.0.2")
    }
    sourceSets["jsTest"].dependencies {
    }
}
