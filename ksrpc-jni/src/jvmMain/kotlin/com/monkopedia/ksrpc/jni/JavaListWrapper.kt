package com.monkopedia.ksrpc.jni

internal open class JavaListWrapper<T>(val list: List<T>) : BasicList<T> {
    override val size: Int
        get() = list.size

    override fun get(index: Int): T = list[index]

    override val asSerialized: JniSerialized
        get() = JniSerialized(this)

    override fun toString(): String {
        return list.toString()
    }
}

internal class JavaMutableListWrapper<T>(val mutableList: MutableList<T>) :
    JavaListWrapper<T>(mutableList), MutableBasicList<T> {
    override fun set(index: Int, value: T) {
        mutableList[index] = value
    }

    override fun add(value: T) {
        mutableList.add(value)
    }

    override fun toString(): String {
        return list.toString()
    }
}

actual fun <T> newList(): MutableBasicList<T> {
    return JavaMutableListWrapper(mutableListOf())
}

fun toSerialized(list: List<Any?>): JniSerialized {
    return JniSerialized(JavaListWrapper(list))
}

fun toList(serialized: JniSerialized): List<Any?> {
    return (serialized.list as JavaListWrapper<*>).list
}
