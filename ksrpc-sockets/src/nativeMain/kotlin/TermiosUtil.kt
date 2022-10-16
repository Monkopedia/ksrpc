/*
 * Copyright 2022 Jason Monk
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
package com.monkopedia.ksrpc.sockets

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind.EXACTLY_ONCE
import kotlin.contracts.contract
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.sizeOf
import platform.posix.ICANON
import platform.posix.STDIN_FILENO
import platform.posix.TCSANOW
import platform.posix.memcpy
import platform.posix.tcgetattr
import platform.posix.tcsetattr
import platform.posix.termios

@OptIn(ExperimentalContracts::class)
inline fun withoutIcanon(fd: Int = STDIN_FILENO, callback: () -> Unit) {
    contract {
        callsInPlace(callback, EXACTLY_ONCE)
    }
    memScoped {
        val old = alloc<termios>()
        initTermios(fd, old.ptr)
        callback()
        resetTermios(fd, old.ptr)
    }
}

fun initTermios(fd: Int = STDIN_FILENO, old: CPointer<termios>) {
    memScoped {
        val current = alloc<termios>().ptr
        tcgetattr(fd, old)
        memcpy(current, old, sizeOf<termios>().toULong())
        current.pointed.c_lflag = current.pointed.c_lflag and ICANON.inv().toUInt()
        tcsetattr(fd, TCSANOW, current)
    }
}

fun resetTermios(fd: Int = STDIN_FILENO, old: CPointer<termios>) {
    tcsetattr(fd, TCSANOW, old)
}
