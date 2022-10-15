package com.monkopedia.ksrpc.sockets.internal

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.errors.IOException
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeStringUtf8

inline fun swallow(function: () -> Unit) {
    try {
        function()
    } catch (t: Throwable) {
    }
}

suspend fun ByteWriteChannel.appendLine(s: String = "") = writeStringUtf8("$s\r\n")

suspend fun ByteReadChannel.readFields(): Map<String, String> {
    val fields = mutableListOf<String>()
    var line = readUTF8Line() ?: throw IOException("$this is closed for reading")
    while (line.isNotEmpty()) {
        fields.add(line)
        line = readUTF8Line() ?: ""
    }
    return parseParams(fields)
}

private fun parseParams(fields: List<String>): Map<String, String> {
    return fields.filter { it.contains(":") }.map {
        val (first, second) = it.splitSingle(':')
        return@map first.trim() to second.trim()
    }.toMap()
}

private fun String.splitSingle(s: Char): Pair<String, String> {
    val index = indexOf(s)
    if (index < 0) throw IllegalArgumentException("Can't find param")
    return substring(0, index) to substring(index + 1, length)
}
