package com.monkopedia.ksrpc.jni

interface BasicList<T> {
    val size: Int

    operator fun get(index: Int): T

    val asSerialized: JniSerialized
}

interface MutableBasicList<T> : BasicList<T> {
    operator fun set(index: Int, value: T)
    fun add(value: T)
}

expect fun <T> newList(): MutableBasicList<T>