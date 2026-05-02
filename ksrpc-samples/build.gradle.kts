import com.monkopedia.ksrpc.local.ksrpcModule

plugins {
    kotlin("multiplatform")
}

ksrpcModule(
    supportNative = false,
    supportJs = false,
    supportWasm = false,
    includePublications = false,
    applyKsrpcPlugin = true
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
        implementation(libs.ktor.server)
        implementation(libs.ktor.server.netty)
        implementation(libs.ktor.client)
        implementation(libs.ktor.client.okhttp)
        implementation(libs.ktor.server.websockets)
        implementation(libs.ktor.client.websockets)
    }
}
