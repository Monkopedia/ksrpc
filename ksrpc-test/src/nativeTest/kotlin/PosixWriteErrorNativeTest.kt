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

import com.monkopedia.ksrpc.sockets.posixFileWriteChannel
import io.ktor.utils.io.errors.IOException
import io.ktor.utils.io.writeStringUtf8
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import platform.posix.EBADF
import platform.posix.F_GETFD
import platform.posix.fcntl

/**
 * A failing `write()` must report which errno failed, and must carry an [IOException] rather
 * than an [IllegalStateException] as its cause.
 *
 * The loop used `error(...)`, which throws [IllegalStateException] and names no errno (#287).
 *
 * What a writer actually catches is ktor's `ClosedWriteChannelException` either way — it wraps
 * whatever the channel was cancelled with — so the observable difference is the **cause** it
 * carries and the message it reports. That is worth stating precisely, because it narrows the
 * concern: a consumer writing `catch (e: IllegalStateException)` around a send does not see
 * the bare `error()` exception, and the `CancellationException`-is-an-`IllegalStateException`
 * ambiguity reaches only code that walks the cause chain. The missing errno reaches everyone.
 */
class PosixWriteErrorNativeTest {

    @Test
    fun writeFailureReportsTheErrnoAsAnIOException() = runBlockingUnit {
        // An fd number that was never open, so the first `write()` fails immediately with
        // EBADF and the writer thread never blocks. Deliberately not a just-closed fd: the
        // channel starts a thread of its own and the runtime can recycle a freed fd number
        // for it, which turns the intended failure into a write to something live.
        val fd = 9999
        check(fcntl(fd, F_GETFD) == -1) { "fd $fd is open; this test needs an invalid fd" }

        val output = posixFileWriteChannel(fd)
        // The failure reaches a writer through the cancelled channel, so keep writing until
        // one throws rather than assuming the first does.
        val thrown = withTimeoutOrNull(5000) {
            var caught: Throwable? = null
            while (caught == null) {
                caught = runCatching {
                    output.writeStringUtf8("x")
                    output.flush()
                }.exceptionOrNull()
                if (caught == null) delay(10)
            }
            caught
        }

        val surfaced = assertNotNull(
            thrown,
            "writing to an invalid fd never surfaced a failure to the caller"
        )
        // The thrown object is ktor's wrapper in both cases, so asserting its type proves
        // nothing — the discriminating facts are one level down.
        val cause = assertNotNull(
            surfaced.cause,
            "the channel was cancelled with no cause, so nothing says why the write failed"
        )
        assertIs<IOException>(
            cause,
            "expected an IOException cause; got ${cause::class.simpleName}, which is what " +
                "`error()` produces and what CancellationException is a subtype of"
        )
        assertContains(cause.message ?: "", "(errno=$EBADF)")
    }
}
