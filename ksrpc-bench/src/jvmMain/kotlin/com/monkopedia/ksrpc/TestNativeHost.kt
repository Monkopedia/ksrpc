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
package com.monkopedia.ksrpc

import com.monkopedia.ksrpc.jni.JniHostInit

/**
 * The benchmark's native binding, mirroring the one ksrpc-test uses. The `@CName`
 * for the native impl is named after this class
 * (`Java_com_monkopedia_ksrpc_TestNativeHost_initialize`); the native side (in the
 * ksrpc-test JNI library this module loads) forwards the [JniHostInit] to
 * `ksrpcHostConnection`, which registers the default `TestJniImpl` service.
 */
object TestNativeHost {
    external fun initialize(host: JniHostInit)
}
