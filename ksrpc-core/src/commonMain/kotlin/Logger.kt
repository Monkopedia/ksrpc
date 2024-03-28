package com.monkopedia.ksrpc

interface Logger {
    fun debug(tag: String, message: String, throwable: Throwable? = null) = Unit
    fun info(tag: String, message: String, throwable: Throwable? = null) = Unit
    fun warn(tag: String, message: String, throwable: Throwable? = null) = Unit
    fun error(tag: String, message: String, throwable: Throwable? = null) = Unit
}