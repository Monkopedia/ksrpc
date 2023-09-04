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
