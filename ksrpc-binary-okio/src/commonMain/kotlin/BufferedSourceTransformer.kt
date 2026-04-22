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
package com.monkopedia.ksrpc.binary.okio

import com.monkopedia.ksrpc.BinaryDataTransformer
import com.monkopedia.ksrpc.BinaryTransformer
import com.monkopedia.ksrpc.Transformer
import com.monkopedia.ksrpc.channels.CallData
import com.monkopedia.ksrpc.channels.SerializedService
import okio.BufferedSource

/**
 * Compiler-plugin target for `okio.BufferedSource` parameters. Adapts
 * okio's [BufferedSource] onto the transport-agnostic
 * [RpcBinaryData][com.monkopedia.ksrpc.channels.RpcBinaryData] surface used
 * by `ksrpc-core`'s [BinaryTransformer].
 *
 * The compiler plugin emits this transformer for `BufferedSource` service
 * signatures; users opt in to okio binary support by adding
 * `ksrpc-binary-okio` to their compile classpath.
 */
object BufferedSourceTransformer :
    Transformer<BufferedSource>,
    BinaryDataTransformer {

    override suspend fun <T> transform(
        input: BufferedSource,
        channel: SerializedService<T>
    ): CallData<T> = BinaryTransformer.transform(input.asRpcBinaryData(), channel)

    override suspend fun <T> untransform(
        data: CallData<T>,
        channel: SerializedService<T>
    ): BufferedSource = BinaryTransformer.untransform(data, channel).asBufferedSource()
}
