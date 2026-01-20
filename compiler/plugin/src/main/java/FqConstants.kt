/*
 * Copyright (C) 2026 Jason Monk <monkopedia@gmail.com>
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

import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

object FqConstants {
    val FQPKG = FqName("com.monkopedia.ksrpc")
    val RPC_ENDPOINT_EXCEPTION = ClassId(FQPKG, Name.identifier("RpcEndpointException"))
    val RPC_OBJECT_KEY = ClassId(FQPKG, Name.identifier("RpcObjectKey"))
    val RPC_SERVICE = ClassId(FQPKG, Name.identifier("RpcService"))
    val FQRPC_SERVICE = FqName("com.monkopedia.ksrpc.RpcService")

    val SERVICE_EXECUTOR = ClassId(FQPKG, Name.identifier("ServiceExecutor"))
    val SERIALIZER_TRANSFORMER = ClassId(FQPKG, Name.identifier("SerializerTransformer"))
    val BINARY_TRANSFORMER = ClassId(FQPKG, Name.identifier("BinaryTransformer"))
    val SUBSERVICE_TRANSFORMER = ClassId(FQPKG, Name.identifier("SubserviceTransformer"))
    val SUSPEND_CLOSEABLE = ClassId(FQPKG, Name.identifier("SuspendCloseable"))

    val CALL_CHANNEL = Name.identifier("callChannel")
    val CLOSE = Name.identifier("close")
    val INVOKE = Name.identifier("invoke")

    val KSERIALIZER = ClassId(FqName("kotlinx.serialization"), Name.identifier("KSerializer"))
    val SERIALIZER_CALLABLE: CallableId =
        CallableId(FqName("kotlinx.serialization"), Name.identifier("serializer"))
    val THREAD_LOCAL = ClassId(FqName("kotlin.native.concurrent"), Name.identifier("ThreadLocal"))

    val BYTE_READ_CHANNEL = FqName("io.ktor.utils.io.ByteReadChannel")

    val CREATE_STUB = Name.identifier("createStub")
    val FIND_ENDPOINT = Name.identifier("findEndpoint")

    val RPC_OBJECT = ClassId(FQPKG, Name.identifier("RpcObject"))
    val RPC_METHOD = ClassId(FQPKG, Name.identifier("RpcMethod"))

    val SERIALIZED_SERVICE =
        ClassId(FqName("com.monkopedia.ksrpc.channels"), Name.identifier("SerializedService"))

    val KS_METHOD = FqName("com.monkopedia.ksrpc.annotation.KsMethod")
    val KS_SERVICE = FqName("com.monkopedia.ksrpc.annotation.KsService")
}
