/**
 * Copyright (C) 2024 Jason Monk <monkopedia@gmail.com>
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

// class NativeByteChannel {
//    suspend fun writeFully(buffer: ChunkBuffer) {
//    }
//
//    suspend fun flush() {
//    }
//
//    val autoFlush: Boolean
// }
//
// private suspend fun ByteReadChannel.copyToImpl(dst: NativeByteChannel, limit: Long): Long {
//    val buffer = ChunkBuffer.Pool.borrow()
//    val dstNeedsFlush = !dst.autoFlush
//
//    try {
//        var copied = 0L
//
//        while (true) {
//            val remaining = limit - copied
//            if (remaining == 0L) break
//            buffer.resetForWrite(minOf(buffer.capacity.toLong(), remaining).toInt())
//
//            val size = readAvailable(buffer)
//            if (size == -1) break
//
//            dst.writeFully(buffer)
//            copied += size
//
//            if (dstNeedsFlush && availableForRead == 0) {
//                dst.flush()
//            }
//        }
//        return copied
//    } catch (t: Throwable) {
//        dst.close(t)
//        throw t
//    } finally {
//        buffer.release(ChunkBuffer.Pool)
//    }
// }
