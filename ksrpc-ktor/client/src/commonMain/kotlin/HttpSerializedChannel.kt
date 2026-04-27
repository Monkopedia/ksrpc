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
@file:OptIn(KsrpcInternal::class)

package com.monkopedia.ksrpc.ktor.internal

import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.KsrpcException
import com.monkopedia.ksrpc.annotation.KsrpcInternal
import com.monkopedia.ksrpc.binary.ktor.asByteReadChannel
import com.monkopedia.ksrpc.binary.ktor.asRpcBinaryData
import com.monkopedia.ksrpc.channels.CallData
import com.monkopedia.ksrpc.channels.ChannelClient
import com.monkopedia.ksrpc.channels.ChannelId
import com.monkopedia.ksrpc.channels.RpcCallId
import com.monkopedia.ksrpc.channels.SerializedChannel
import com.monkopedia.ksrpc.channels.SerializedService
import com.monkopedia.ksrpc.internal.ClientChannelContext
import com.monkopedia.ksrpc.internal.SubserviceChannel
import com.monkopedia.ksrpc.ktor.DEFAULT_KSRPC_ERROR_CODE_TO_HTTP_STATUS
import com.monkopedia.ksrpc.ktor.KSRPC_ERROR_CODE_HEADER
import com.monkopedia.ksrpc.ktor.KSRPC_ERROR_MESSAGE_HEADER
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.encodeURLPath
import io.ktor.utils.io.ByteReadChannel
import kotlin.coroutines.CoroutineContext

private const val KSRPC_BINARY = "binary"
private const val KSRPC_CHANNEL = "channel"

internal class HttpSerializedChannel(
    private val httpClient: HttpClient,
    private val baseStripped: String,
    override val env: KsrpcEnvironment<String>,
    private val errorCodeToHttpStatus: Map<Int, Int> = DEFAULT_KSRPC_ERROR_CODE_TO_HTTP_STATUS
) : SerializedChannel<String>,
    ChannelClient<String> {

    private val onCloseHandlers = mutableSetOf<suspend () -> Unit>()
    override val context: CoroutineContext =
        ClientChannelContext(this) + env.coroutineExceptionHandler

    /** Inverse of [errorCodeToHttpStatus] for status -> code recovery on receive. */
    private val httpStatusToErrorCode: Map<Int, Int> =
        errorCodeToHttpStatus.entries.associate { (code, status) -> status to code }

    override suspend fun call(
        channelId: ChannelId,
        endpoint: String,
        data: CallData<String>,
        callId: RpcCallId?
    ): CallData<String> {
        val response = httpClient.post(
            "$baseStripped/call/${endpoint.encodeURLPath()}"
        ) {
            accept(ContentType.Application.Json)
            headers[KSRPC_BINARY] = data.isBinary.toString()
            headers[KSRPC_CHANNEL] = channelId.id
            setBody(
                if (data.isBinary) data.readBinary().asByteReadChannel() else data.readSerialized()
            )
        }
        // Recover the ksrpc error code from headers / mapping. Header takes precedence so a
        // non-default mapping can still surface the user's original code; otherwise fall
        // back to the inverse of [errorCodeToHttpStatus]. If neither identifies the response
        // as an error, treat it as a normal payload.
        val headerCode = response.headers[KSRPC_ERROR_CODE_HEADER]?.toIntOrNull()
        val statusCode = response.status.value
        val mappedFromStatus = httpStatusToErrorCode[statusCode]
        val errorCode = headerCode ?: mappedFromStatus
        if (errorCode != null) {
            val message = response.headers[KSRPC_ERROR_MESSAGE_HEADER] ?: response.body<String>()
            val payload = response.body<String>().takeIf { it.isNotEmpty() }
            return CallData.Error(errorCode, message, payload)
        }
        if (response.headers[KSRPC_BINARY]?.toBoolean() == true) {
            return CallData.createBinary(response.body<ByteReadChannel>().asRpcBinaryData())
        }
        return CallData.create(response.body<String>())
    }

    override suspend fun close(id: ChannelId) {
        call(id, "", CallData.create("{}"), callId = null)
    }

    override suspend fun wrapChannel(channelId: ChannelId): SerializedService<String> {
        env.logger.debug("HttpChannel", "Wrapping channel ${channelId.id}")
        return SubserviceChannel(this, channelId)
    }

    override suspend fun close() {
        onCloseHandlers.forEach { it.invoke() }
    }

    override suspend fun onClose(onClose: suspend () -> Unit) {
        onCloseHandlers.add(onClose)
    }
}

@Suppress("unused")
internal suspend fun HttpResponse.checkErrors() {
    // Retained as an internal hook for downstream callers; the new error-propagation path
    // routes through the [CallData.Error] variant returned by [HttpSerializedChannel.call],
    // so this no longer needs to throw on its own. The body is left intact for the caller.
}

@Suppress("unused")
internal val INTERNAL_ERROR_CODE_FOR_BACK_COMPAT: Int = KsrpcException.INTERNAL_ERROR_CODE
