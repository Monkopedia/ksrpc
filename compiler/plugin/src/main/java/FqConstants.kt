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
package com.monkopedia.ksrpc.plugin

object FqConstants {
    const val PKG = "com.monkopedia.ksrpc"
    const val RPC_ENDPOINT_EXCEPTION = "$PKG.RpcEndpointException"
    const val RPC_OBJECT = "$PKG.RpcObject"
    const val RPC_SERVICE = "$PKG.RpcService"
    const val SERIALIZED_CHANNEL = "$PKG.SerializedChannel"
    const val RPC_METHOD = "$PKG.RpcMethod"
    const val SERVICE_EXECUTOR = "$PKG.ServiceExecutor"
    const val SERIALIZER_TRANSFORMER = "$PKG.SerializerTransformer"
    const val BINARY_TRANSFORMER = "$PKG.BinaryTransformer"
    const val SUBSERVICE_TRANSFORMER = "$PKG.SubserviceTransformer"

    const val CREATE_STUB = "createStub"
    const val FIND_ENDPOINT = "findEndpoint"
    const val CALL_CHANNEL = "callChannel"
    const val CALL = "call"
    const val INVOKE = "invoke"

    const val KSERIALIZER = "kotlinx.serialization.KSerializer"
    const val SERIALIZER = "kotlinx.serialization.serializer"
    const val TYPE_OF = "kotlin.reflect.typeOf"
    const val BYTE_READ_CHANNEL = "io.ktor.utils.io.ByteReadChannel"
    const val THREAD_LOCAL = "kotlin.native.concurrent.ThreadLocal"
}
