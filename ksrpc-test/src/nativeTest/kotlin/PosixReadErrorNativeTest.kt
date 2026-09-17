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
import kotlin.test.assertNotNull
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import platform.posix.F_GETFD
import platform.posix.fcntl

/**
 * A failing `read()` must close the channel **with a cause**.
 *
 * The read loop used to leave the loop on any negative return without consulting `errno`,
 * exactly as it does at EOF, so `finally` closed the channel with no cause. A consumer could
 * not tell a dead transport from a peer that had finished talking (#281).
 *
 * This pins the "real error" half. **The `EINTR` half is not covered**: delivering a signal to
 * the internal reader thread while it is blocked in `read()` is not something this suite can
 * do deterministically — the thread is created inside [posixFileReadChannel] and never
 * exposed — so no test here distinguishes the retry from its absence.
 */
class PosixReadErrorNativeTest {

    @Test
    fun readFailureClosesTheChannelWithACause() = runBlockingUnit {
        // An fd number that was never open, so the first `read()` fails immediately with EBADF
        // and the reader thread never blocks. Deliberately NOT a closed pipe fd: the channel
        // starts a thread of its own, and the runtime can recycle a just-freed fd number for
        // it, which turns the intended failure back into a live blocking read.
        val fd = 9999
        check(fcntl(fd, F_GETFD) == -1) { "fd $fd is open; this test needs an invalid fd" }

        val channel = posixFileReadChannel(fd)
        // Poll rather than awaitContent(): reading is what throws the cause, and the assertion
        // below is about what the channel reports, not about how the read fails.
        withTimeoutOrNull(5000) {
            while (channel.closedCause == null && !channel.isClosedForRead) {
                delay(10)
            }
        }

        assertNotNull(
            channel.closedCause,
            "read() failed with EBADF but the channel closed with no cause — a consumer " +
                "cannot distinguish that from a healthy EOF (#281)"
        )
    }
}
