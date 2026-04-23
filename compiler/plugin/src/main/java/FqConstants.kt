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
    val RPC_ENDPOINT_EXCEPTION =
        ClassId(FQPKG, Name.identifier("RpcEndpointException"))
    val RPC_OBJECT_KEY = ClassId(FQPKG, Name.identifier("RpcObjectKey"))
    val RPC_SERVICE = ClassId(FQPKG, Name.identifier("RpcService"))
    val CONTINUATION = ClassId(FqName("kotlin.coroutines"), Name.identifier("Continuation"))
    val INTROSPECTION_SERVICE_FQ = FqName("com.monkopedia.ksrpc.IntrospectionService")
    val INTROSPECTION_SERVICE = ClassId(FQPKG, Name.identifier("IntrospectionService"))
    val INTROSPECTABLE_RPC_SERVICE = ClassId(FQPKG, Name.identifier("IntrospectableRpcService"))
    val FQ_INTROSPECTABLE_RPC_SERVICE = FqName("com.monkopedia.ksrpc.IntrospectableRpcService")
    val INTROSPECTION_SERVICE_IMPL = ClassId(FQPKG, Name.identifier("IntrospectionServiceImpl"))
    val FQRPC_SERVICE = FqName("com.monkopedia.ksrpc.RpcService")

    val SERVICE_EXECUTOR =
        ClassId(FqName("com.monkopedia.ksrpc.internal"), Name.identifier("ServiceExecutor"))
    val SERIALIZER_TRANSFORMER = ClassId(FQPKG, Name.identifier("SerializerTransformer"))

    /**
     * Core transport-agnostic binary transformer (operates on `RpcBinaryData`).
     * Not emitted directly for user `ByteReadChannel` signatures — those go
     * through [BYTE_READ_CHANNEL_TRANSFORMER] which adapts onto this one.
     */
    val BINARY_TRANSFORMER = ClassId(FQPKG, Name.identifier("BinaryTransformer"))

    /**
     * Transformer emitted for methods with `ByteReadChannel` inputs or outputs.
     * Lives in `ksrpc-binary-ktor`, the dedicated ktor-io adapter module.
     * Resolved optionally: consumers who never use `ByteReadChannel` in a
     * service signature do not need `ksrpc-binary-ktor` on their classpath.
     * The ktor transports depend on it transitively, so any consumer using
     * `ByteReadChannel` through one of them gets the symbol for free; direct
     * users (no transport) opt in by declaring `ksrpc-binary-ktor`.
     */
    val BYTE_READ_CHANNEL_TRANSFORMER = ClassId(
        FqName("com.monkopedia.ksrpc.binary.ktor"),
        Name.identifier("ByteReadChannelTransformer")
    )

    /**
     * Transformer emitted for methods with `kotlinx.io.Source` inputs or
     * outputs. Lives in `ksrpc-binary-kotlinx-io`, the dedicated kotlinx.io
     * adapter module. Resolved optionally: consumers who never use `Source`
     * in a service signature do not need `ksrpc-binary-kotlinx-io` on their
     * classpath.
     */
    val SOURCE_TRANSFORMER = ClassId(
        FqName("com.monkopedia.ksrpc.binary.kxio"),
        Name.identifier("SourceTransformer")
    )

    /**
     * Transformer emitted for methods with `okio.BufferedSource` inputs or
     * outputs. Lives in `ksrpc-binary-okio`, the dedicated okio adapter
     * module. Resolved optionally: consumers who never use `BufferedSource`
     * in a service signature do not need `ksrpc-binary-okio` on their
     * classpath.
     */
    val BUFFERED_SOURCE_TRANSFORMER = ClassId(
        FqName("com.monkopedia.ksrpc.binary.okio"),
        Name.identifier("BufferedSourceTransformer")
    )
    val SUBSERVICE_TRANSFORMER = ClassId(FQPKG, Name.identifier("SubserviceTransformer"))
    val SUSPEND_CLOSEABLE = ClassId(FQPKG, Name.identifier("SuspendCloseable"))

    val CALL_CHANNEL = Name.identifier("callChannel")
    val CLOSE = Name.identifier("close")
    val INVOKE = Name.identifier("invoke")
    val GET_INTROSPECTION = Name.identifier("getIntrospection")
    val KSERIALIZER = ClassId(FqName("kotlinx.serialization"), Name.identifier("KSerializer"))
    val SERIALIZER_CALLABLE: CallableId =
        CallableId(FqName("kotlinx.serialization"), Name.identifier("serializer"))
    val THREAD_LOCAL = ClassId(FqName("kotlin.native.concurrent"), Name.identifier("ThreadLocal"))

    val BYTE_READ_CHANNEL = FqName("io.ktor.utils.io.ByteReadChannel")
    val KOTLINX_IO_SOURCE = FqName("kotlinx.io.Source")
    val OKIO_BUFFERED_SOURCE = FqName("okio.BufferedSource")

    val CREATE_STUB = Name.identifier("createStub")
    val FIND_ENDPOINT = Name.identifier("findEndpoint")
    val SERVICE_NAME = Name.identifier("serviceName")
    val ENDPOINTS = Name.identifier("endpoints")
    val OBJ = Name.identifier("Obj")

    val RPC_OBJECT = ClassId(FQPKG, Name.identifier("RpcObject"))
    val RPC_OBJECT_FACTORY = ClassId(FQPKG, Name.identifier("RpcObjectFactory"))
    val RESOLVE_SERIALIZER_OR_THROW: CallableId =
        CallableId(FQPKG, Name.identifier("resolveSerializerOrThrow"))
    val RPC_METHOD = ClassId(FQPKG, Name.identifier("RpcMethod"))
    val ARITY = Name.identifier("arity")
    val CREATE = Name.identifier("create")
    val KTYPE = ClassId(FqName("kotlin.reflect"), Name.identifier("KType"))

    val SERIALIZED_SERVICE =
        ClassId(FqName("com.monkopedia.ksrpc.channels"), Name.identifier("SerializedService"))

    val KS_METHOD = FqName("com.monkopedia.ksrpc.annotation.KsMethod")
    val KS_SERVICE = FqName("com.monkopedia.ksrpc.annotation.KsService")
    val KS_INTROSPECTABLE = FqName("com.monkopedia.ksrpc.annotation.KsIntrospectable")
    val KS_METHOD_METADATA = FqName("com.monkopedia.ksrpc.annotation.KsMethodMetadata")
    val KS_NOTIFICATION = FqName("com.monkopedia.ksrpc.annotation.KsNotification")
    val KS_ERROR = FqName("com.monkopedia.ksrpc.annotation.KsError")

    // kotlinx.serialization.Serializable annotation FQN — used by @KsError validation
    // to check that the bound error payload type is @Serializable.
    val KOTLINX_SERIALIZABLE = FqName("kotlinx.serialization.Serializable")

    // Ksrpc runtime classes used to materialize @KsError bindings into
    // `List<KsErrorMapping>` at codegen time.
    val KS_ERROR_MAPPING = ClassId(FQPKG, Name.identifier("KsErrorMapping"))

    val METHOD_METADATA = ClassId(FQPKG, Name.identifier("MethodMetadata"))
    val METADATA_VALUE = ClassId(FQPKG, Name.identifier("MetadataValue"))
    val METADATA_VALUE_STRING =
        METADATA_VALUE.createNestedClassId(Name.identifier("StringValue"))
    val METADATA_VALUE_INT = METADATA_VALUE.createNestedClassId(Name.identifier("IntValue"))
    val METADATA_VALUE_LONG = METADATA_VALUE.createNestedClassId(Name.identifier("LongValue"))
    val METADATA_VALUE_BOOLEAN =
        METADATA_VALUE.createNestedClassId(Name.identifier("BooleanValue"))
    val METADATA_VALUE_DOUBLE =
        METADATA_VALUE.createNestedClassId(Name.identifier("DoubleValue"))
    val METADATA_VALUE_FLOAT =
        METADATA_VALUE.createNestedClassId(Name.identifier("FloatValue"))
    val METADATA_VALUE_KCLASS =
        METADATA_VALUE.createNestedClassId(Name.identifier("KClassValue"))
    val METADATA_VALUE_ENUM =
        METADATA_VALUE.createNestedClassId(Name.identifier("EnumValue"))
    val METADATA_VALUE_LIST =
        METADATA_VALUE.createNestedClassId(Name.identifier("ListValue"))

    val PAIR = ClassId(FqName("kotlin"), Name.identifier("Pair"))
    val TO_FUNCTION = CallableId(FqName("kotlin"), Name.identifier("to"))

    // ksrpc-flow runtime types — referenced by StubGeneration / ObjGeneration when
    // emitting `FlowSubserviceTransformer<T>(KsFlowService.Obj<T>(serializer))` IR
    // for `Flow<T>` signatures (issue #39). Resolved optimistically; the compiler
    // guards with `KsrpcGenerationEnvironment.flowSupported`.
    val FLOW = FqName("kotlinx.coroutines.flow.Flow")
    val KSRPC_FLOW_PKG = FqName("com.monkopedia.ksrpc.flow")
    val KS_FLOW_SERVICE = ClassId(KSRPC_FLOW_PKG, Name.identifier("KsFlowService"))
    val FLOW_TRANSFORMER =
        ClassId(KSRPC_FLOW_PKG, Name.identifier("FlowSubserviceTransformer"))
}
