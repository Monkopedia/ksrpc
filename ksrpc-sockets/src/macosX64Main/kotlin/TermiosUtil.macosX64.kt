package com.monkopedia.ksrpc.sockets

import platform.posix.ICANON
import platform.posix.termios

actual fun termios.setICanon() {
    c_lflag = c_lflag and ICANON.inv().toULong()
}

