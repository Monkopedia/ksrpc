package com.monkopedia.ksrpc.internal

import com.monkopedia.ksrpc.CallData
import com.monkopedia.ksrpc.ERROR_PREFIX
import com.monkopedia.ksrpc.KSRPC_BINARY
import com.monkopedia.ksrpc.RpcFailure
import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLPath
import io.ktor.utils.io.readRemaining
import kotlinx.serialization.json.Json

internal class HttpPacketExchanger(private val client: HttpClient, private val baseStripped: String) :
    PacketExchangerBase(Json) {
    override suspend fun call(packet: Packet): Packet {
        require(packet.input) {
            "HttpClient.asPacketChannel doesn't support non-input packets"
        }
        val input = packet.data
        val response = client.post<HttpResponse>(
            "$baseStripped/call/${packet.endpoint.encodeURLPath()}"
        ) {
            accept(ContentType.Application.Json)
            headers[KSRPC_BINARY] = input.isBinary.toString()
            body = if (input.isBinary) input.readBinary() else input.readSerialized()
        }
        response.checkErrors()
        if (response.headers[KSRPC_BINARY]?.toBoolean() == true) {
            return packet.copy(data = CallData.create(response.content))
        }
        return packet.copy(data = CallData.create(response.content.readRemaining().readText()))
    }

    override suspend fun close() {
    }
}

internal suspend fun HttpResponse.checkErrors() {
    if (status == HttpStatusCode.InternalServerError) {
        val text = receive<String>()
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