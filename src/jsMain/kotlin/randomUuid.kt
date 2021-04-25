package com.monkopedia.ksrpc

import nanoid.nanoid

actual fun randomUuid(): String {
    return nanoid()
}