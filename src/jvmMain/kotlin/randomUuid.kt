package com.monkopedia.ksrpc

import com.aventrix.jnanoid.jnanoid.NanoIdUtils

actual fun randomUuid(): String {
    return NanoIdUtils.randomNanoId()
}