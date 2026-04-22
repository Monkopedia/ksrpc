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
package com.monkopedia.ksrpc.binary.kxio

import com.monkopedia.ksrpc.BinaryDataTransformer
import com.monkopedia.ksrpc.BinaryTransformer
import com.monkopedia.ksrpc.Transformer
import com.monkopedia.ksrpc.channels.CallData
import com.monkopedia.ksrpc.channels.SerializedService
import kotlinx.io.Source

/**
 * Compiler-plugin target for `kotlinx.io.Source` parameters. Adapts
 * kotlinx.io's [Source] onto the transport-agnostic
 * [RpcBinaryData][com.monkopedia.ksrpc.channels.RpcBinaryData] surface used by
 * `ksrpc-core`'s [BinaryTransformer].
 *
 * The compiler plugin emits this transformer for `Source` service signatures;
 * users opt in to kotlinx.io binary support by adding
 * `ksrpc-binary-kotlinx-io` to their compile classpath.
 */
object SourceTransformer :
    Transformer<Source>,
    BinaryDataTransformer {

    override suspend fun <T> transform(input: Source, channel: SerializedService<T>): CallData<T> =
        BinaryTransformer.transform(input.asRpcBinaryData(), channel)

    override suspend fun <T> untransform(data: CallData<T>, channel: SerializedService<T>): Source =
        BinaryTransformer.untransform(data, channel).asSource()
}
