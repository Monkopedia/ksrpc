package com.monkopedia.ksrpc

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.serialization.StringFormat
import kotlinx.serialization.json.Json

interface KsrpcElement {
    val env: KsrpcEnvironment
}

interface KsrpcEnvironment : Serializing {
    val defaultScope: CoroutineScope
    val errorListener: ErrorListener
}

fun ksrpcEnvironment(builder: KsrpcEnvironmentBuilder.() -> Unit): KsrpcEnvironment {
    return KsrpcEnvironmentBuilder().also(builder)
}

data class KsrpcEnvironmentBuilder internal constructor(
    override var serialization: StringFormat = Json { },
    override var defaultScope: CoroutineScope = GlobalScope,
    override var errorListener: ErrorListener = ErrorListener { }
) : KsrpcEnvironment
