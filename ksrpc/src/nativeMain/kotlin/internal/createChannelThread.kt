package com.monkopedia.ksrpc.internal

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.newSingleThreadContext

internal actual fun maybeCreateChannelThread(): CoroutineDispatcher =
    newSingleThreadContext("Channel Thread")
