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
package com.monkopedia.ksrpc.jni

import com.monkopedia.ksrpc.jni.NativeScopeHandler.cancelScope
import com.monkopedia.ksrpc.jni.NativeScopeHandler.createNativeScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job

/**
 * Maps the lifecycle of a scope into a matching native scope.
 *
 * Note that this adopts the behavior of a SupervisorJob where the
 * cancellation of the native stop does not cancel the receiver scope.
 */
val CoroutineScope.asNativeScope: Long
    get() {
        val nativeScope = createNativeScope()
        coroutineContext.job.invokeOnCompletion {
            cancelScope(nativeScope)
        }
        return nativeScope
    }

object NativeScopeHandler {
    external fun createNativeScope(): Long
    external fun cancelScope(scope: Long)
}
