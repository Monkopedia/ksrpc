/**
 * Copyright (C) 2025 Jason Monk <monkopedia@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
