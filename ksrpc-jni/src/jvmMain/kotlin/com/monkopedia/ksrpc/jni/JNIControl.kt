package com.monkopedia.ksrpc.jni

object JNIControl {
    private var hasInited = false
    private var threads = 0

    fun ensureInit() {
        if (!hasInited) {
            initJniDispatcher(1)
        }
    }

    fun initJniDispatcher(threads: Int) {
        hasInited = true
        val newThreads =
            if (threads > this.threads) (this.threads until threads) else IntRange.EMPTY
        this.threads = threads
        updateThreads(threads)
        newThreads.forEach {
            DispatchThread(it).start()
        }
    }

    private external fun updateThreads(threads: Int)
    private external fun executeThread(id: Int)

    private class DispatchThread(private val id: Int) : Thread() {
        override fun run() {
            executeThread(id)
            if (id < threads) {
                DispatchThread(id).start()
            }
        }
    }
}
