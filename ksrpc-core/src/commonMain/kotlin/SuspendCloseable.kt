/**
 * Copyright (C) 2022 Jason Monk <monkopedia@gmail.com>
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
package com.monkopedia.ksrpc

/**
 * Version of Closeable that has suspending close.
 */
interface SuspendCloseable {
    /**
     * Called when the interaction with this object is done and its resources can be cleaned up.
     */
    suspend fun close()
}

/**
 * Used for implementations of [SuspendCloseable] that need observers attached to be notified
 * when [SuspendCloseable.close] is called.
 */
interface SuspendCloseableObservable : SuspendCloseable {
    /**
     * Add a callback to be invoked when [SuspendCloseable.close] is called.
     */
    suspend fun onClose(onClose: suspend () -> Unit)
}

/**
 * Helper that runs [usage] then invokes [SuspendCloseable.close] in the finally block.
 */
suspend inline fun <T : SuspendCloseable, R> T.use(usage: (T) -> R): R {
    try {
        return usage(this)
    } finally {
        close()
    }
}
