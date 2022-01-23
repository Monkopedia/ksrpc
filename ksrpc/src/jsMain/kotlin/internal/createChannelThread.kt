package com.monkopedia.ksrpc.internal

import kotlinx.coroutines.CloseableCoroutineDispatcher
import kotlinx.coroutines.Dispatchers

// No threads in JS, just use main dispatcher.
internal actual fun createChannelThread(): CloseableCoroutineDispatcher = Dispatchers.Main