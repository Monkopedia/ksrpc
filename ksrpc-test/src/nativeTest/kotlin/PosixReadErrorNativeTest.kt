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
@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.ksrpc

import com.monkopedia.ksrpc.sockets.posixFileReadChannel
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertNotNull
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import platform.posix.EBADF
import platform.posix.EISDIR
import platform.posix.F_GETFD
import platform.posix.O_RDONLY
import platform.posix.fcntl
import platform.posix.open

/**
 * A failing `read()` must close the channel **with a cause**.
 *
 * The read loop used to leave on any negative return without consulting `errno`, exactly as
 * it does at EOF, so `finally` closed the channel with no cause and a consumer could not tell
 * a dead transport from a peer that had finished talking (#281).
 *
 * `EBADF` is covered separately because treating it as an end of stream was proposed and
 * rejected, and a decision that only lives in a review is one the next change re-litigates.
 *
 * **The `EINTR` half is not covered**: delivering a signal to the internal reader thread while
 * it is blocked in `read()` is not something this suite can do deterministically — the thread
 * is created inside [posixFileReadChannel] and never exposed — so no test here distinguishes
 * the retry from its absence.
 */
class PosixReadErrorNativeTest {

    @Test
    fun readFailureClosesTheChannelWithACause() = runBlockingUnit {
        // A directory fd: `read` on it fails with EISDIR every time, on the very first call.
        // A real error with no closing involved, so nothing here can race or be recycled.
        val fd = open(".", O_RDONLY)
        check(fd >= 0) { "could not open a directory fd" }

        val channel = posixFileReadChannel(fd)
        // Poll rather than awaitContent(): reading is what raises the cause, and the assertion
        // is about what the channel reports, not about how the read fails.
        withTimeoutOrNull(5000) {
            while (channel.closedCause == null && !channel.isClosedForRead) {
                delay(10)
            }
        }

        val cause = assertNotNull(
            channel.closedCause,
            "read() failed with EISDIR but the channel closed with no cause — a consumer " +
                "cannot distinguish that from a healthy EOF (#281)"
        )
        // Which errno, not merely that something failed: without this both tests here pass
        // against a loop that reports one fixed errno for everything.
        assertContains(cause.message ?: "", "errno=$EISDIR")
    }

    /**
     * `EBADF` is reported like any other failure, and that is a decision rather than an
     * oversight.
     *
     * Treating it as an end of stream would read as "the caller closed the fd to stop us",
     * but closing an fd another thread is blocked reading does not reliably unblock that read,
     * so it is not a teardown signal this can depend on — while an fd that was never valid
     * produces it every time. Silently ending the stream there would recreate exactly the
     * failure-looks-like-EOF confusion this class exists to prevent.
     */
    @Test
    fun anInvalidFdIsReportedRatherThanReadAsEndOfStream() = runBlockingUnit {
        // An fd number that was never open, so the first `read()` returns EBADF immediately.
        // Deliberately not a just-closed fd: this channel starts a thread, and the runtime can
        // recycle a freed fd number for it, turning the intended EBADF into a blocking read.
        val fd = 9999
        check(fcntl(fd, F_GETFD) == -1) { "fd $fd is open; this test needs an invalid fd" }

        val channel = posixFileReadChannel(fd)
        withTimeoutOrNull(5000) {
            while (channel.closedCause == null && !channel.isClosedForRead) {
                delay(10)
            }
        }

        val cause = assertNotNull(
            channel.closedCause,
            "EBADF closed the channel with no cause, so an fd that was never valid is " +
                "indistinguishable from a peer that finished talking"
        )
        assertContains(cause.message ?: "", "errno=$EBADF")
    }
}
