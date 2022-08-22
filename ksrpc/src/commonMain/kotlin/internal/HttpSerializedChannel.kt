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
package com.monkopedia.ksrpc.internal

import com.monkopedia.ksrpc.ERROR_PREFIX
import com.monkopedia.ksrpc.KSRPC_BINARY
import com.monkopedia.ksrpc.KSRPC_CHANNEL
import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.RpcFailure
import com.monkopedia.ksrpc.channels.CallData
import com.monkopedia.ksrpc.channels.ChannelClient
import com.monkopedia.ksrpc.channels.ChannelId
import com.monkopedia.ksrpc.channels.SerializedChannel
import com.monkopedia.ksrpc.channels.SerializedService
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
import kotlinx.serialization.json.Json
import kotlin.coroutines.CoroutineContext

internal class HttpSerializedChannel(
    private val httpClient: HttpClient,
    private val baseStripped: String,
    override val env: KsrpcEnvironment
) : SerializedChannel, ChannelClient {

    private val onCloseHandlers = mutableSetOf<suspend () -> Unit>()
    override val context: CoroutineContext =
        ClientChannelContext(this) + env.coroutineExceptionHandler

    override suspend fun call(channelId: ChannelId, endpoint: String, input: CallData): CallData {
        val response = httpClient.post(
            "$baseStripped/call/${endpoint.encodeURLPath()}"
        ) {
            accept(ContentType.Application.Json)
            headers[KSRPC_BINARY] = input.isBinary.toString()
            headers[KSRPC_CHANNEL] = channelId.id
            setBody(if (input.isBinary) input.readBinary() else input.readSerialized())
        }
        response.checkErrors()
        if (response.headers[KSRPC_BINARY]?.toBoolean() == true) {
            return CallData.create(response.body<ByteReadChannel>())
        }
        return CallData.create(response.body<String>())
    }

    override suspend fun close(id: ChannelId) {
        call(id, "", CallData.create("{}"))
    }

    override suspend fun wrapChannel(channelId: ChannelId): SerializedService {
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
    }
}
