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
package com.monkopedia.ksrpc

import com.monkopedia.ksrpc.internal.SubserviceChannel
import kotlin.native.concurrent.ThreadLocal
import kotlinx.serialization.Serializable
import kotlinx.serialization.StringFormat
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

fun interface ErrorListener {
    fun onError(t: Throwable)
}

expect val Throwable.asString: String

internal const val ERROR_PREFIX = "ERROR:"

fun SerializedChannel.subservice(serviceId: String, json: StringFormat? = null): SerializedChannel {
    return SubserviceChannel(json ?: serialization, this, serviceId)
}

private const val SPLIT_CHAR = ":"
@ThreadLocal
private val LIST_SERIALIZER = ListSerializer(String.serializer())

fun StringFormat.encodedEndpoint(endpoint: List<String>): String {
    val list = encodeToString(LIST_SERIALIZER, endpoint.subList(1, endpoint.size))
    return "${endpoint.first()}$SPLIT_CHAR$list"
}

fun StringFormat.decodedEndpoint(endpoint: String): Pair<String, List<String>?> {
    val index = endpoint.indexOf(SPLIT_CHAR)
    if (index < 0) {
        return endpoint.trimStart('/') to null
    }
    return endpoint.substring(0, index).trimStart('/') to
        decodeFromString(LIST_SERIALIZER, endpoint.substring(index + 1))
}

@Serializable
data class RpcFailure(val stack: String) {
    fun toException(): RuntimeException {
        return RpcException(stack)
    }
}

class RpcException(override val message: String) : RuntimeException(message)
