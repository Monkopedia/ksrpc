/*
 * Copyright 2020 Jason Monk
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
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
rootProject.name = "ksrpc"
include(":ksrpc-api")
include(":ksrpc-core")

include(":ksrpc-jsonrpc")

include(":ksrpc-packets")
include(":ksrpc-server")
include(":ksrpc-sockets")
include(":ksrpc-web-worker")
include(":ksrpc-web-worker-test")
include(":ksrpc-jni")
include(":ksrpc-test")

include(":ksrpc-ktor-client")
project(":ksrpc-ktor-client").projectDir = file("ksrpc-ktor/client")
include(":ksrpc-ktor-server")
project(":ksrpc-ktor-server").projectDir = file("ksrpc-ktor/server")
include(":ksrpc-ktor-websocket-shared")
project(":ksrpc-ktor-websocket-shared").projectDir = file("ksrpc-ktor/websocket/shared")
include(":ksrpc-ktor-websocket-client")
project(":ksrpc-ktor-websocket-client").projectDir = file("ksrpc-ktor/websocket/client")
include(":ksrpc-ktor-websocket-server")
project(":ksrpc-ktor-websocket-server").projectDir = file("ksrpc-ktor/websocket/server")

includeBuild("compiler")
