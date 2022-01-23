package com.monkopedia.ksrpc.internal

import kotlinx.coroutines.CloseableCoroutineDispatcher
import kotlinx.coroutines.newSingleThreadContext

internal actual fun createChannelThread(): CloseableCoroutineDispatcher =
    newSingleThreadContext("Channel thread")