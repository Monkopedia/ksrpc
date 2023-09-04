package com.monkopedia.ksrpc.jni

fun interface NativeKsrpcEnvironmentFactory {
    fun createNativeEnvironment(): Long
}
