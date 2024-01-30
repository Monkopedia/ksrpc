/**
 * Copyright (C) 2024 Jason Monk <monkopedia@gmail.com>
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
package com.monkopedia.ksrpc.ktor.internal

import com.monkopedia.ksrpc.ERROR_PREFIX
import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.RpcEndpointException
import com.monkopedia.ksrpc.RpcFailure
import com.monkopedia.ksrpc.channels.CallData
import com.monkopedia.ksrpc.channels.ChannelClient
import com.monkopedia.ksrpc.channels.ChannelId
import com.monkopedia.ksrpc.channels.SerializedChannel
import com.monkopedia.ksrpc.channels.SerializedService
import com.monkopedia.ksrpc.internal.ClientChannelContext
import com.monkopedia.ksrpc.internal.SubserviceChannel
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLPath
import io.ktor.utils.io.ByteReadChannel
import kotlin.coroutines.CoroutineContext
import kotlinx.serialization.json.Json

private const val KSRPC_BINARY = "binary"
private const val KSRPC_CHANNEL = "channel"

internal class HttpSerializedChannel(
    private val httpClient: HttpClient,
    private val baseStripped: String,
    override val env: KsrpcEnvironment<String>
) : SerializedChannel<String>, ChannelClient<String> {

    private val onCloseHandlers = mutableSetOf<suspend () -> Unit>()
    override val context: CoroutineContext =
        ClientChannelContext(this) + env.coroutineExceptionHandler

    override suspend fun call(
        channelId: ChannelId,
        endpoint: String,
        data: CallData<String>
    ): CallData<String> {
        val response = httpClient.post(
            "$baseStripped/call/${endpoint.encodeURLPath()}"
        ) {
            accept(ContentType.Application.Json)
            headers[KSRPC_BINARY] = data.isBinary.toString()
            headers[KSRPC_CHANNEL] = channelId.id
            setBody(if (data.isBinary) data.readBinary() else data.readSerialized())
        }
        response.checkErrors()
        if (response.headers[KSRPC_BINARY]?.toBoolean() == true) {
            return CallData.createBinary(response.body<ByteReadChannel>())
        }
        return CallData.create(response.body<String>())
    }

    override suspend fun close(id: ChannelId) {
        call(id, "", CallData.create("{}"))
    }

    override suspend fun wrapChannel(channelId: ChannelId): SerializedService<String> {
        return SubserviceChannel(this, channelId)
    }

    override suspend fun close() {
        onCloseHandlers.forEach { it.invoke() }
    }

    override suspend fun onClose(onClose: suspend () -> Unit) {
        onCloseHandlers.add(onClose)
    }
}

internal suspend fun HttpResponse.checkErrors() {
    if (status == HttpStatusCode.InternalServerError) {
        val text = body<String>()
        if (text.startsWith(ERROR_PREFIX)) {
            throw Json.decodeFromString(
                RpcFailure.serializer(),
                text.substring(ERROR_PREFIX.length)
            ).toException()
        } else {
            throw IllegalStateException("Can't parse error $this")
        }
    } else if (status == HttpStatusCode.NotFound) {
        throw RpcEndpointException("Url not found $this")
    }
}
