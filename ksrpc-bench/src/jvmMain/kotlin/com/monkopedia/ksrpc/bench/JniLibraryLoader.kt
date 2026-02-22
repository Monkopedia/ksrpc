/*
 * Copyright (C) 2026 Jason Monk <monkopedia@gmail.com>
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
package com.monkopedia.ksrpc.bench

import com.monkopedia.ksrpc.jni.NativeUtils

internal object JniLibraryLoader {
    @Volatile
    private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val path = candidatePaths.firstOrNull {
                NativeUtils::class.java.getResourceAsStream(it) != null
            } ?: error(
                "Missing JNI benchmark library resource. " +
                    "Expected one of: ${candidatePaths.joinToString()}."
            )
            NativeUtils.loadLibraryFromJar(path)
            loaded = true
        }
    }

    private val candidatePaths = listOf(
        "/libs/libksrpc_test.so",
        "/libs/libksrpc_test.dylib"
    )
}
