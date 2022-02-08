package com.monkopedia.ksrpc

import java.io.PrintWriter
import java.io.StringWriter
import kotlin.reflect.full.companionObjectInstance


actual inline fun <reified T : RpcService> rpcObject(): RpcObject<T> {
    return T::class.companionObjectInstance as RpcObject<T>
}

actual val Throwable.asString: String
    get() = StringWriter().also {
        printStackTrace(PrintWriter(it))
    }.toString()