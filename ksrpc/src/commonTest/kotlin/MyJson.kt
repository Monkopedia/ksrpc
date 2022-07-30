package com.monkopedia.ksrpc

import kotlinx.serialization.Serializable

@Serializable
data class MyJson(
    val str: String,
    val int: Int,
    val nFloat: Float?
)